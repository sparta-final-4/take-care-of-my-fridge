package org.example.takecareofmyfridgebe.infra.elasticsearch.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 재색인 실패 경로를 ES 없이 확인한다. ES 클라이언트는 목이다
@SuppressWarnings("unchecked")
class EsIndexerReindexTest {

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);
    private final ElasticsearchIndicesClient indices = mock(ElasticsearchIndicesClient.class);
    private final EsDocumentReader reader = mock(EsDocumentReader.class);
    private final EsIndexProperties index = new RecipeIndexProperties(
            "recipes", new ByteArrayResource("{\"settings\": {}}".getBytes(StandardCharsets.UTF_8)), null, null, 500, false);

    private final EsIndexer indexer = new EsIndexer(esClient, new SynonymRuleBuilder(JsonMapper.builder().build())) {
        @Override
        protected EsIndexProperties indexProperties() {
            return index;
        }

        @Override
        protected EsDocumentReader documentReader() {
            return reader;
        }
    };

    @BeforeEach
    void setUp() {
        when(esClient.indices()).thenReturn(indices);
        when(reader.readAll()).thenReturn(List.of(new EsDocument(1L, Map.of("name", "김치찌개"))));
    }

    // indices().delete(...)에 넘긴 인덱스 이름들
    private List<String> deletedIndices() throws IOException {
        ArgumentCaptor<Function<DeleteIndexRequest.Builder, ObjectBuilder<DeleteIndexRequest>>> captor =
                ArgumentCaptor.forClass(Function.class);
        verify(indices).delete(captor.capture());
        return captor.getValue().apply(new DeleteIndexRequest.Builder()).build().index();
    }

    private static BulkResponse bulkOk() {
        return BulkResponse.of(b -> b.errors(false).took(1).items(List.of()));
    }

    private static CountResponse countOf(long count) {
        return CountResponse.of(c -> c.count(count).shards(s -> s.total(1).successful(1).failed(0)));
    }

    @Test
    @DisplayName("bulk 색인이 실패하면 새 인덱스를 지우고 원래 예외를 던지며, alias는 옮기지 않는다")
    void deletesNewIndexWhenBulkFails() throws IOException {
        // Given
        IOException bulkFailure = new IOException("ES 연결 실패");
        when(esClient.bulk(any(Function.class))).thenThrow(bulkFailure);

        // When
        IOException thrown = assertThrows(IOException.class, indexer::reindex);

        // Then
        assertEquals(bulkFailure, thrown);
        List<String> deleted = deletedIndices();
        assertEquals(1, deleted.size());
        assertTrue(deleted.get(0).startsWith("recipes_"));
        verify(indices, never()).updateAliases(any(Function.class));
    }

    @Test
    @DisplayName("건수가 맞지 않으면 새 인덱스를 지우고 alias는 옮기지 않는다")
    void deletesNewIndexWhenCountMismatches() throws IOException {
        // Given
        when(esClient.bulk(any(Function.class))).thenReturn(bulkOk());
        when(esClient.count(any(Function.class))).thenReturn(countOf(0));

        // When & Then
        assertThrows(IllegalStateException.class, indexer::reindex);
        assertTrue(deletedIndices().get(0).startsWith("recipes_"));
        verify(indices, never()).updateAliases(any(Function.class));
    }

    @Test
    @DisplayName("alias를 옮긴 뒤 이전 인덱스 삭제가 실패해도 재색인은 성공으로 끝난다")
    void succeedsWhenOldIndexDeleteFails() throws IOException {
        // Given
        when(esClient.bulk(any(Function.class))).thenReturn(bulkOk());
        when(esClient.count(any(Function.class))).thenReturn(countOf(1));
        when(indices.existsAlias(any(Function.class))).thenReturn(new BooleanResponse(true));
        when(indices.getAlias(any(Function.class))).thenReturn(
                GetAliasResponse.of(g -> g.aliases("recipes_old", a -> a.aliases(Map.of()))));
        when(indices.delete(any(Function.class))).thenThrow(new IOException("삭제 실패"));

        // When
        String newIndex = indexer.reindex();

        // Then
        assertTrue(newIndex.startsWith("recipes_"));
        verify(indices).updateAliases(any(Function.class));
        assertEquals(List.of("recipes_old"), deletedIndices());
    }
}

package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.ingredient.dto.IngredientIndexSource;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IngredientIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.VectorProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientDocumentReaderTest {

    private static final String ALIASES = """
            {"달걀": {"계란": "달걀", "신선 란": "달걀"}}
            """;

    private final IngredientService ingredientService = mock(IngredientService.class);
    private final RecipeEmbedder embedder = mock(RecipeEmbedder.class);
    private final IngredientAliasDictionary aliasDictionary = new IngredientAliasDictionary(new ObjectMapper());

    private IngredientDocumentReader reader(boolean embedding) {
        RecipeSearchProperties properties = new RecipeSearchProperties(
                SearchEngine.ELASTICSEARCH,
                new IndexProperties(null, null, new IngredientIndexProperties(
                        "ingredients", null, null, null, 500,
                        List.of(new ByteArrayResource(ALIASES.getBytes(StandardCharsets.UTF_8))), embedding)),
                new VectorProperties(false, 3, 20, 100, 0.5f, 100));
        return new IngredientDocumentReader(ingredientService, aliasDictionary, embedder, properties);
    }

    @Test
    @DisplayName("문서의 aliases에는 표준명 자신과 별칭이 공백 없는 소문자 키로 들어간다")
    void readAll_includesNameAndAliasKeys() {
        // Given
        when(ingredientService.findIndexSources()).thenReturn(List.of(
                new IngredientIndexSource(1L, "달걀"),
                new IngredientIndexSource(2L, "절인 배추")));

        // When
        List<EsDocument> docs = reader(false).readAll();

        // Then
        Map<String, Object> egg = docs.get(0).source();
        assertThat(docs.get(0).id()).isEqualTo(1L);
        assertThat(egg.get("name")).isEqualTo("달걀");
        assertThat(egg.get("aliases")).isEqualTo(List.of("달걀", "계란", "신선란"));
        assertThat(docs.get(1).source().get("aliases")).isEqualTo(List.of("절인배추"));
        assertThat(egg.containsKey("embedding")).isFalse();
        verify(embedder, never()).embedAll(anyList());
    }

    @Test
    @DisplayName("재료 임베딩을 켜면 이름과 별칭을 함께 임베딩해 넣는다 (레시피 벡터 검색 설정과 무관)")
    void readAll_attachesEmbedding_whenEmbeddingEnabled() {
        // Given
        when(ingredientService.findIndexSources()).thenReturn(List.of(new IngredientIndexSource(1L, "달걀")));
        when(embedder.embedAll(List.of("달걀 (계란, 신선 란)"))).thenReturn(List.of(new float[]{1, 2, 3}));

        // When
        List<EsDocument> docs = reader(true).readAll();

        // Then
        assertThat((float[]) docs.get(0).source().get("embedding")).containsExactly(new float[]{1, 2, 3});
    }

    @Test
    @DisplayName("임베딩이 실패해도 벡터 없이 색인 문서를 만든다")
    void readAll_indexesWithoutEmbedding_whenEmbeddingFails() {
        // Given
        when(ingredientService.findIndexSources()).thenReturn(List.of(new IngredientIndexSource(1L, "달걀")));
        when(embedder.embedAll(anyList())).thenThrow(new IllegalStateException("OpenAI down"));

        // When
        List<EsDocument> docs = reader(true).readAll();

        // Then
        assertThat(docs.size()).isEqualTo(1);
        assertThat(docs.get(0).source().containsKey("embedding")).isFalse();
    }

    @Test
    @DisplayName("단건 색인은 색인 대상이 아니면 빈 결과를 돌려준다")
    void readOne_returnsEmpty_whenNotIndexTarget() {
        when(ingredientService.findIndexSource(9L)).thenReturn(Optional.empty());

        assertThat(reader(false).readOne(9L).isEmpty()).isTrue();
    }

}

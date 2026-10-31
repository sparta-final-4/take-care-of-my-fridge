package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIndexSource;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeIndexSourceService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeDocumentReaderEmbeddingTest {

    private final RecipeIndexSourceService recipeIndexSourceService = mock(RecipeIndexSourceService.class);
    private final RecipeEmbedder embedder = mock(RecipeEmbedder.class);

    private RecipeDocumentReader reader(boolean embedding) {
        RecipeSearchProperties properties = new RecipeSearchProperties(
                SearchEngine.ELASTICSEARCH,
                new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500, embedding), null, null),
                null);
        return new RecipeDocumentReader(recipeIndexSourceService, embedder, properties);
    }

    private RecipeIndexSource recipe(long id, String name, String summary, String tags, List<RecipeIndexSource.Ingredient> ingredients) {
        return new RecipeIndexSource(id, "TEST", "SRC_" + id, name, summary, "한식", "찌개", "끓이기", null,
                null, null, 2, null, null, null, null, null, null, tags, null, null, null, ingredients, List.of("끓인다"));
    }

    @Test
    @DisplayName("임베딩 텍스트는 이름, 요약, 분류, 재료(중복 제거), 태그를 줄로 나눠 넣고 조리 단계는 뺀다")
    void embeddingTextJoinsSearchableFields() {
        // Given
        RecipeIndexSource kimchiStew = recipe(1L, "김치찌개", " 칼칼한 국물 ", "매운맛, 찌개", List.of(
                new RecipeIndexSource.Ingredient("김치", "주재료"),
                new RecipeIndexSource.Ingredient("돼지고기", "주재료"),
                new RecipeIndexSource.Ingredient("김치", "부재료")));

        // When
        String text = RecipeDocumentReader.embeddingText(kimchiStew);

        // Then
        assertEquals("""
                김치찌개
                칼칼한 국물
                분류: 한식, 찌개, 끓이기
                재료: 김치, 돼지고기
                태그: 매운맛, 찌개""", text);
    }

    @Test
    @DisplayName("요약, 재료, 태그가 비어 있으면 그 줄을 넣지 않는다")
    void embeddingTextSkipsEmptyFields() {
        // Given
        RecipeIndexSource plain = recipe(1L, "흰밥", null, null, List.of());

        // When
        String text = RecipeDocumentReader.embeddingText(plain);

        // Then
        assertEquals("흰밥\n분류: 한식, 찌개, 끓이기", text);
    }

    @Test
    @DisplayName("임베딩을 켜면 readAll은 모든 레시피를 한 번에 넘겨 입력 순서대로 벡터를 붙인다")
    void readAllAttachesEmbeddingsInOrder() {
        // Given
        when(recipeIndexSourceService.findAll()).thenReturn(List.of(
                recipe(1L, "두부조림", null, null, List.of()),
                recipe(2L, "계란말이", null, null, List.of())));
        when(embedder.embedAll(anyList())).thenReturn(List.of(new float[]{1, 0}, new float[]{2, 0}));

        // When
        List<EsDocument> docs = reader(true).readAll();

        // Then
        verify(embedder, times(1)).embedAll(List.of(
                "두부조림\n분류: 한식, 찌개, 끓이기",
                "계란말이\n분류: 한식, 찌개, 끓이기"));
        assertEquals(1L, docs.get(0).id());
        assertArrayEquals(new float[]{1, 0}, (float[]) docs.get(0).source().get("embedding"));
        assertArrayEquals(new float[]{2, 0}, (float[]) docs.get(1).source().get("embedding"));
    }

    @Test
    @DisplayName("임베딩을 켜면 readOne도 벡터를 붙인다")
    void readOneAttachesEmbedding() {
        // Given
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "두부조림", null, null, List.of())));
        when(embedder.embedAll(anyList())).thenReturn(List.of(new float[]{1, 0}));

        // When
        EsDocument doc = reader(true).readOne(1L).orElseThrow();

        // Then
        assertArrayEquals(new float[]{1, 0}, (float[]) doc.source().get("embedding"));
    }

    @Test
    @DisplayName("임베딩이 실패하면 예외 없이 embedding 필드만 빼고 색인한다")
    void indexesWithoutEmbeddingWhenEmbedderFails() {
        // Given
        when(recipeIndexSourceService.findAll()).thenReturn(List.of(recipe(1L, "두부조림", null, null, List.of())));
        when(embedder.embedAll(anyList())).thenThrow(new IllegalStateException("OpenAI 장애"));

        // When
        List<EsDocument> docs = reader(true).readAll();

        // Then
        assertEquals(1, docs.size());
        assertEquals("두부조림", docs.get(0).source().get("name"));
        assertFalse(docs.get(0).source().containsKey("embedding"));
    }

    @Test
    @DisplayName("임베딩을 끄면 임베딩을 호출하지 않고 embedding 필드도 넣지 않는다")
    void skipsEmbeddingWhenDisabled() {
        // Given
        when(recipeIndexSourceService.findAll()).thenReturn(List.of(recipe(1L, "두부조림", null, null, List.of())));

        // When
        List<EsDocument> docs = reader(false).readAll();

        // Then
        assertFalse(docs.get(0).source().containsKey("embedding"));
        verify(embedder, never()).embedAll(anyList());
    }

}

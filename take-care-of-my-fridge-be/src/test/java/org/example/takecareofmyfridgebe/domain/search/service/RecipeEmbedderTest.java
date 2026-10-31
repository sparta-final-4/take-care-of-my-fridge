package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.VectorProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeEmbedderTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final RecipeSearchProperties properties = new RecipeSearchProperties(
            SearchEngine.ELASTICSEARCH,
            null,
            new VectorProperties(true, 3, 20, 100, 0.5f, 2));
    private final RecipeEmbedder embedder = new RecipeEmbedder(embeddingModel, properties);

    @Test
    @DisplayName("검색어 한 건을 설정한 차원의 벡터로 바꾼다")
    void embedsOneText() {
        // Given
        when(embeddingModel.embed("두부")).thenReturn(new float[]{1, 2, 3});

        // When
        float[] vector = embedder.embed("두부");

        // Then
        assertArrayEquals(new float[]{1, 2, 3}, vector);
    }

    @Test
    @DisplayName("색인용 임베딩은 batchSize 단위로 나눠 호출하고 입력 순서를 지킨다")
    void embedAllSplitsByBatchSize() {
        // Given
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(List.of(new float[]{1, 0, 0}, new float[]{2, 0, 0}));
        when(embeddingModel.embed(List.of("c"))).thenReturn(List.of(new float[]{3, 0, 0}));

        // When
        List<float[]> vectors = embedder.embedAll(List.of("a", "b", "c"));

        // Then
        assertEquals(3, vectors.size());
        assertEquals(1f, vectors.get(0)[0]);
        assertEquals(2f, vectors.get(1)[0]);
        assertEquals(3f, vectors.get(2)[0]);
        verify(embeddingModel, times(2)).embed(anyList());
    }

    @Test
    @DisplayName("모델이 설정과 다른 차원을 돌려주면 예외를 던진다")
    void rejectsWrongDimensions() {
        // Given
        when(embeddingModel.embed("두부")).thenReturn(new float[]{1, 0});

        // When & Then
        assertThrows(IllegalStateException.class, () -> embedder.embed("두부"));
    }

}

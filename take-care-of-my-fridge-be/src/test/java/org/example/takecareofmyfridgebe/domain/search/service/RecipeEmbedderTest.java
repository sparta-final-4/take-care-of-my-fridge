package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.VectorProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void embed_returnsVector() {
        // Given
        when(embeddingModel.embed("두부")).thenReturn(new float[]{1, 2, 3});

        // When
        float[] vector = embedder.embed("두부");

        // Then
        assertThat(vector).containsExactly(new float[]{1, 2, 3});
    }

    @Test
    @DisplayName("색인용 임베딩은 batchSize 단위로 나눠 호출하고 입력 순서를 지킨다")
    void embedAll_splitsByBatchSize() {
        // Given
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(List.of(new float[]{1, 0, 0}, new float[]{2, 0, 0}));
        when(embeddingModel.embed(List.of("c"))).thenReturn(List.of(new float[]{3, 0, 0}));

        // When
        List<float[]> vectors = embedder.embedAll(List.of("a", "b", "c"));

        // Then
        assertThat(vectors.size()).isEqualTo(3);
        assertThat(vectors.get(0)[0]).isEqualTo(1f);
        assertThat(vectors.get(1)[0]).isEqualTo(2f);
        assertThat(vectors.get(2)[0]).isEqualTo(3f);
        verify(embeddingModel, times(2)).embed(anyList());
    }

    @Test
    @DisplayName("모델이 설정과 다른 차원을 돌려주면 예외를 던진다")
    void embed_throwException_whenDimensionsMismatch() {
        // Given
        when(embeddingModel.embed("두부")).thenReturn(new float[]{1, 0});

        // When & Then
        assertThatThrownBy(() -> embedder.embed("두부")).isInstanceOf(IllegalStateException.class);
    }

}

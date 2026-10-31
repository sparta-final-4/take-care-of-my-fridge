package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 레시피 검색용 임베딩. 검색어 한 건과 색인용 문서 여러 건을 벡터로 바꾼다.
 * <br>
 * Spring AI EmbeddingModel만 감싼다. 모델을 바꿔도 이 클래스 밖은 바뀌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RecipeEmbedder {

    private final EmbeddingModel embeddingModel;
    private final RecipeSearchProperties properties;

    // 검색어 한 건
    public float[] embed(String text) {
        return checkDimensions(embeddingModel.embed(text));
    }

    // 색인용: batchSize 단위로 나눠 호출하고, 입력 순서대로 돌려준다
    public List<float[]> embedAll(List<String> texts) {
        int batchSize = properties.vector().batchSize();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            for (float[] vector : embeddingModel.embed(batch)) {
                vectors.add(checkDimensions(vector));
            }
        }
        return vectors;
    }

    // 모델 설정이 매핑과 어긋나면 ES가 거절하기 전에 여기서 멈춘다
    private float[] checkDimensions(float[] vector) {
        int expected = properties.vector().dimensions();
        if (vector.length != expected) {
            throw new IllegalStateException("임베딩 차원 불일치: 기대 %d / 실제 %d".formatted(expected, vector.length));
        }
        return vector;
    }

}

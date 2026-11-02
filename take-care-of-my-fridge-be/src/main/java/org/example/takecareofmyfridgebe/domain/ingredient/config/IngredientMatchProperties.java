package org.example.takecareofmyfridgebe.domain.ingredient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 재료명 → 표준 재료 매칭 기준 (IngredientMatcher).
 *
 * @param exactMatchScore  후보 1등 점수가 이 값 이상이면 그대로 채택한다. 1.0은 이름·별칭이 정확히 같은 경우
 * @param suffixMinLength  끝부분 규칙에 쓸 후보 이름의 최소 글자 수(공백 제외). 한 글자("파", "무")는 너무 많은 이름의 끝과 겹쳐 뺀다
 */
@ConfigurationProperties(prefix = "app.ingredient")
public record IngredientMatchProperties(
        @DefaultValue("1.0")
        double exactMatchScore,
        @DefaultValue("2")
        int suffixMinLength
) {
}

package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * AI 상담 레시피 후보 검색 설정.
 *
 * @param excludedCategories 후보에서 뺄 레시피 분류(recipes.category 값). 요리 추천에 맞지 않는 양념·음료 등
 * @param minOwnedRatio      보유 비율(가진 재료 / 기본 양념 뺀 재료) 기준. 미만인 레시피는 기준 이상 레시피로
 *                           10개가 안 찰 때만 뒤에 채운다(선택 재료 2개 이상을 전부 쓰는 레시피는 예외). 0이면 기준 없이 정렬 순서대로 고른다
 */
@ConfigurationProperties(prefix = "app.chat.candidate")
public record ChatCandidateProperties(
        @DefaultValue({"양념", "음료"})
        List<String> excludedCategories,
        @DefaultValue("0.4")
        double minOwnedRatio
) {
}

package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * AI 상담 레시피 후보 검색 설정.
 *
 * @param excludedCategories 후보에서 뺄 레시피 분류(recipes.category 값). 요리 추천에 맞지 않는 양념·음료 등
 */
@ConfigurationProperties(prefix = "app.chat.candidate")
public record ChatCandidateProperties(
        @DefaultValue({"양념", "음료"})
        List<String> excludedCategories
) {
}

package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 레시피 자세히 답을 만들 재료: 그 레시피 카드 하나(recommendations 이벤트)와 AI에 넘길 [레시피 정보] 줄 글.
 *
 * @param event      카드 하나만 든 recommendations 이벤트 내용
 * @param recipeInfo 레시피 자세히 프롬프트에 넣을 레시피 정보 (RecipeDetailPromptBuilder)
 */
public record RecipeDetailReply(
        ChatRecommendationsEventResponse event,
        String recipeInfo
) {
}

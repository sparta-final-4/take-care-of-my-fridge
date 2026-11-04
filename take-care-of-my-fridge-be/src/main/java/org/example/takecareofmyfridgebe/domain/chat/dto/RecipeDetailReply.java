package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 레시피 자세히 답을 만들 재료: 그 레시피 카드 하나(recommendations 이벤트)와 AI에 넘길 [레시피 정보] 줄 글, 넘길 질문.
 *
 * @param event      카드 하나만 든 recommendations 이벤트 내용 (카드 설명은 "물어보신 레시피예요"라 이유 줄 대신 보인다)
 * @param recipeInfo 레시피 자세히 프롬프트에 넣을 레시피 정보 (RecipeDetailPromptBuilder)
 * @param question   AI에 사용자 말 대신 넘길 질문 (DetailQuestion). 정하기 전이면 null
 */
public record RecipeDetailReply(
        ChatRecommendationsEventResponse event,
        String recipeInfo,
        String question
) {

    public RecipeDetailReply(ChatRecommendationsEventResponse event, String recipeInfo) {
        this(event, recipeInfo, null);
    }

    public RecipeDetailReply withQuestion(String newQuestion) {
        return new RecipeDetailReply(event, recipeInfo, newQuestion);
    }
}

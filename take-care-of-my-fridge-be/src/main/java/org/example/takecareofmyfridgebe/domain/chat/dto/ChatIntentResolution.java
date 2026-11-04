package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 상담 메시지를 어떻게 처리할지 정한 결과.
 *
 * @param intent         RECOMMEND·REFINE이면 recipeQuery로 후보를 찾고, RECIPE_DETAIL이면 targetRecipeId 레시피를 자세히 답하고,
 *                       GENERAL이면 일반 답을 한다
 * @param source         의도를 정한 방법
 * @param recipeQuery    추천 조건 (REFINE이면 직전 조건과 합친 것). RECOMMEND·REFINE이 아니면 null
 * @param targetRecipeId RECIPE_DETAIL일 때 물어본 레시피 ID. 그 밖에는 null
 * @param detailQuestion RECIPE_DETAIL일 때 질문 종류 (모르면 null, OTHER처럼 다룬다). 그 밖에는 null
 */
public record ChatIntentResolution(
        ChatIntent intent,
        Source source,
        RecipeQuery recipeQuery,
        Long targetRecipeId,
        DetailQuestion detailQuestion
) {

    public enum Source {
        // 칩(mode=RECOMMEND)이라 AI 판단 없이 추천
        MODE,
        // AI가 판단
        AI,
        // AI 판단이 실패해 선택 재료 유무로 정함 (있으면 추천, 없으면 일반)
        FALLBACK
    }

    public static ChatIntentResolution general(Source source) {
        return new ChatIntentResolution(ChatIntent.GENERAL, source, null, null, null);
    }

    public static ChatIntentResolution recommend(Source source, RecipeQuery recipeQuery) {
        return new ChatIntentResolution(ChatIntent.RECOMMEND, source, recipeQuery, null, null);
    }

    public static ChatIntentResolution refine(Source source, RecipeQuery recipeQuery) {
        return new ChatIntentResolution(ChatIntent.REFINE, source, recipeQuery, null, null);
    }

    public static ChatIntentResolution recipeDetail(Source source, Long recipeId) {
        return recipeDetail(source, recipeId, null);
    }

    public static ChatIntentResolution recipeDetail(Source source, Long recipeId, DetailQuestion question) {
        return new ChatIntentResolution(ChatIntent.RECIPE_DETAIL, source, null, recipeId, question);
    }

    // 후보를 찾아 카드로 추천하는지 (새 추천·다시 추천)
    public boolean recommends() {
        return (intent == ChatIntent.RECOMMEND || intent == ChatIntent.REFINE) && recipeQuery != null;
    }
}

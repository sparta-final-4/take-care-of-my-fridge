package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 상담 메시지를 어떻게 처리할지 정한 결과.
 *
 * @param intent      RECOMMEND면 recipeQuery로 후보를 찾고, GENERAL이면 일반 답을 한다
 * @param source      의도를 정한 방법
 * @param recipeQuery 추천 조건. GENERAL이면 null
 */
public record ChatIntentResolution(
        ChatIntent intent,
        Source source,
        RecipeQuery recipeQuery
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
        return new ChatIntentResolution(ChatIntent.GENERAL, source, null);
    }

    public static ChatIntentResolution recommend(Source source, RecipeQuery recipeQuery) {
        return new ChatIntentResolution(ChatIntent.RECOMMEND, source, recipeQuery);
    }
}

package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

import java.util.List;

/**
 * 상담 메시지를 어떻게 처리할지 정한 결과.
 *
 * @param intent         RECOMMEND면 recipeQuery로 후보를 찾고, RECIPE_DETAIL이면 targetRecipeId 레시피를 자세히 답하고,
 *                       FRIDGE면 lookup으로 냉장고를, SHOP이면 살 상품을 보여 주고, GENERAL이면 일반 답을 한다. OFF_TOPIC·BLOCKED·SELF_HARM이면 답 생성 AI 없이 서버 안내로 답한다(declines)
 * @param source         의도를 정한 방법
 * @param recipeQuery    추천 조건 (이어짐이면 직전 조건과 합친 것). RECOMMEND가 아니면 null
 * @param targetRecipeId RECIPE_DETAIL일 때 물어본 레시피 ID. 그 밖에는 null
 * @param detailQuestion RECIPE_DETAIL일 때 질문 종류 (모르면 null, OTHER처럼 다룬다). 그 밖에는 null
 * @param allergens      이 상담에서 사용자가 말한 알레르기·못 먹는 재료 이름 (추천이면 그 재료를 recipeQuery의 뺄 재료에 더했다). 없으면 빈 목록
 * @param lookup         FRIDGE·SHOP일 때 볼 범위와 말한 재료·상품 이름. 그 밖에는 null
 * @param follows        이어짐: 직전 추천의 조건을 이어받아 일부만 바꾼 추천인지 (직전 조건과 합쳤고, 후보에서 직전 카드를 뺀다).
 *                       직전 추천(Redis 직전 조건)이 없으면 늘 false
 */
public record ChatIntentResolution(
        ChatIntent intent,
        Source source,
        RecipeQuery recipeQuery,
        Long targetRecipeId,
        DetailQuestion detailQuestion,
        List<String> allergens,
        ItemLookup lookup,
        boolean follows
) {

    public ChatIntentResolution {
        allergens = allergens == null ? List.of() : List.copyOf(allergens);
    }

    public ChatIntentResolution(ChatIntent intent, Source source, RecipeQuery recipeQuery, Long targetRecipeId,
                                DetailQuestion detailQuestion) {
        this(intent, source, recipeQuery, targetRecipeId, detailQuestion, List.of(), null, false);
    }

    /**
     * 냉장고 보기에서 볼 것.
     *
     * @param scope       범위 (재료 이름을 말했으면 무시하고 그 재료만 본다)
     * @param names       말한 재료·상품 이름 (말한 그대로). 없으면 빈 목록
     * @param saleOnly    사기에서 할인 중인 상품을 물었는지 ("세일하는 재료 있어?")
     * @param productType 할인 상품을 물을 때 상품 종류 ("밀키트" → MEALKIT, "재료" → INGREDIENT). 말하지 않았으면 null(둘 다)
     */
    public record ItemLookup(FridgeScope scope, List<String> names, boolean saleOnly, ProductType productType) {
        public ItemLookup {
            scope = scope == null ? FridgeScope.ALL : scope;
            names = names == null ? List.of() : List.copyOf(names);
        }

        public ItemLookup(FridgeScope scope, List<String> names) {
            this(scope, names, false, null);
        }
    }

    public static ChatIntentResolution fridge(Source source, ItemLookup lookup) {
        return new ChatIntentResolution(ChatIntent.FRIDGE, source, null, null, null, List.of(), lookup, false);
    }

    public static ChatIntentResolution shop(Source source, ItemLookup lookup) {
        return new ChatIntentResolution(ChatIntent.SHOP, source, null, null, null, List.of(), lookup, false);
    }

    public enum Source {
        // 칩(mode=RECOMMEND)이라 AI 판단 없이 추천
        MODE,
        // AI가 판단
        AI,
        // AI가 실패해(Jev 실패, 꺼내기가 필요한 의도인데 OpenAI 꺼내기 실패) 질문에 답하지 않고 서버가 추천 카드로 답함.
        // 선택 재료 → 냉장고 재료 → 인기 레시피 순 (ChatRecommendationFacade.serverFallback). AI를 다시 부르지 않는다
        FALLBACK
    }

    // AI 실패 시 서버 답 (선택 재료와 선택 재료만 쓰기만 있는 조건)
    public static ChatIntentResolution serverFallback(RecipeQuery recipeQuery) {
        return new ChatIntentResolution(ChatIntent.RECOMMEND, Source.FALLBACK, recipeQuery, null, null);
    }

    // AI 실패 시 서버 답인지 (질문에 답하지 않고 서버 카드와 정해진 글로 답한다)
    public boolean serverFallback() {
        return source == Source.FALLBACK;
    }

    public static ChatIntentResolution general(Source source) {
        return new ChatIntentResolution(ChatIntent.GENERAL, source, null, null, null);
    }

    public static ChatIntentResolution recommend(Source source, RecipeQuery recipeQuery) {
        return new ChatIntentResolution(ChatIntent.RECOMMEND, source, recipeQuery, null, null);
    }

    // 직전 추천의 조건을 이어받는 추천 (recipeQuery는 직전 조건과 합친 것)
    public static ChatIntentResolution recommendFollowing(Source source, RecipeQuery recipeQuery) {
        return new ChatIntentResolution(ChatIntent.RECOMMEND, source, recipeQuery, null, null, List.of(), null, true);
    }

    public static ChatIntentResolution recipeDetail(Source source, Long recipeId) {
        return recipeDetail(source, recipeId, null);
    }

    public static ChatIntentResolution recipeDetail(Source source, Long recipeId, DetailQuestion question) {
        return new ChatIntentResolution(ChatIntent.RECIPE_DETAIL, source, null, recipeId, question);
    }

    // AI가 답 대신 서버 안내로 답할 의도 (상관없는 질문, 목록 밖 욕설·탈취, 자해)
    public static ChatIntentResolution declined(ChatIntent intent) {
        return new ChatIntentResolution(intent, Source.AI, null, null, null);
    }

    // 답 생성 AI를 부르지 않고 서버 안내로 답하는지 (의도 판단 AI를 썼으므로 하루 횟수는 센다)
    public boolean declines() {
        return intent == ChatIntent.OFF_TOPIC || intent == ChatIntent.BLOCKED || intent == ChatIntent.SELF_HARM;
    }

    // 알레르기 재료를 뺄 재료에 더한 결과 (추천이 아니면 이름만 둔다)
    public ChatIntentResolution withAllergens(List<String> names, List<Long> ingredientIds) {
        RecipeQuery query = recipeQuery == null ? null : recipeQuery.withExcludedIngredients(ingredientIds);
        return new ChatIntentResolution(intent, source, query, targetRecipeId, detailQuestion, names, lookup, follows);
    }

    // 후보를 찾아 카드로 추천하는지 (새 추천·이어짐)
    public boolean recommends() {
        return intent == ChatIntent.RECOMMEND && recipeQuery != null;
    }
}

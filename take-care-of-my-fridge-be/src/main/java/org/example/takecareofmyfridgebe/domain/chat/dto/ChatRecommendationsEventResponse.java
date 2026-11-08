package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상담 추천 카드 SSE 이벤트(event: recommendations). 추천 설명 글보다 먼저 보낸다.
 *
 * @param recipes  추천 레시피 (최대 3개, 서버가 고른 순서). 밀키트만 보여 주는 답이면 빈 목록
 * @param mealkits    추천 밀키트 (레시피 카드 아래). 말에 밀키트 단어가 있으면 최대 3개, 번거로워하는 말이면 1개, 그 밖에는 빈 목록
 * @param fridgeItems 냉장고 보기 카드 (소비기한 순, 지난 줄은 맨 아래, 최대 10개). 이 답에서만 보여 주고 저장하지 않는다. 그 밖에는 빈 목록
 * @param relaxed     후보를 찾으려고 푼 조건 (조건 풀기 전이라 지금은 빈 목록)
 * @param empty       보여 줄 카드가 하나도 없는지
 */
public record ChatRecommendationsEventResponse(
        List<RecommendedRecipe> recipes,
        List<RecommendedMealkit> mealkits,
        List<FridgeItemCard> fridgeItems,
        List<String> relaxed,
        boolean empty
) {

    public ChatRecommendationsEventResponse {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
        mealkits = mealkits == null ? List.of() : List.copyOf(mealkits);
        fridgeItems = fridgeItems == null ? List.of() : List.copyOf(fridgeItems);
        relaxed = relaxed == null ? List.of() : List.copyOf(relaxed);
    }

    public ChatRecommendationsEventResponse(List<RecommendedRecipe> recipes, List<String> relaxed, boolean empty) {
        this(recipes, List.of(), List.of(), relaxed, empty);
    }

    // 냉장고 보기 카드만 있는 이벤트
    public static ChatRecommendationsEventResponse fridge(List<FridgeItemCard> items) {
        return new ChatRecommendationsEventResponse(List.of(), List.of(), items, List.of(), items.isEmpty());
    }

    public static ChatRecommendationsEventResponse of(List<RecommendedRecipe> recipes) {
        return of(recipes, List.of());
    }

    public static ChatRecommendationsEventResponse of(List<RecommendedRecipe> recipes, List<RecommendedMealkit> mealkits) {
        return new ChatRecommendationsEventResponse(recipes, mealkits, List.of(), List.of(), recipes.isEmpty() && mealkits.isEmpty());
    }

    /**
     * 냉장고 보기 카드 하나 (냉장고 줄 하나).
     *
     * @param fridgeId     냉장고 줄 ID
     * @param ingredientId 재료 ID
     * @param name         보여 줄 이름 (일반 재료는 재료 이름, 밀키트·직접 입력은 등록 이름)
     * @param rawName      등록 이름 (구매 때 상품 이름 등)
     * @param quantity     남은 수량
     * @param unit         수량 단위
     * @param dDay         오늘 기준 소비기한까지 남은 일수 (0이면 오늘까지, 음수면 지남)
     * @param status       소비기한 상태 (NORMAL·IMMINENT·EXPIRED)
     * @param mealkit      밀키트 줄인지
     * @param productId    구매 확정으로 들어온 줄의 상품 ID (없으면 null)
     * @param imageUrl     밀키트 줄이면 연결 상품 이미지 (없으면 null)
     */
    public record FridgeItemCard(
            Long fridgeId,
            Long ingredientId,
            String name,
            String rawName,
            BigDecimal quantity,
            String unit,
            long dDay,
            FridgeItemStatus status,
            boolean mealkit,
            Long productId,
            String imageUrl
    ) {
    }

    // 같은 레시피 카드에 밀키트를 붙인 이벤트
    public ChatRecommendationsEventResponse withMealkits(List<RecommendedMealkit> picked) {
        return of(recipes, picked);
    }

    /**
     * 추천 밀키트 카드 하나. 누르면 상품 상세로 간다.
     *
     * @param product     담을 상품 (가격·할인가·품절은 카드를 만들 때 기준, 용량 칸은 null)
     * @param servings    구성 인분
     * @param recipeId    이 밀키트로 만드는 레시피 ID (연결이 없으면 null)
     * @param recipeName  그 레시피 이름 (연결이 없거나 레시피가 지워졌으면 null)
     * @param description 카드 아래 서버 설명 (레시피 카드 아래 한 장이면 "요리하기 번거로우면 …", 밀키트만 보여 주면 null)
     */
    public record RecommendedMealkit(
            ShoppingProduct product,
            int servings,
            Long recipeId,
            String recipeName,
            String description
    ) {
    }

    /**
     * 추천 카드 하나.
     *
     * @param cookTimeMin          조리 시간(분), 없으면 null
     * @param usedIngredients      쓰는 냉장고 재료 이름 (임박한 순)
     * @param imminentIngredients  그중 소비기한 임박(D-3 이내) 재료
     * @param matchedTastes        겹친 맛(한글). 사용자가 메시지로 맛을 말했으면 그 맛, 아니면 회원 선호 맛
     * @param missingIngredients   냉장고에 없는 재료 (기본 양념 제외). 재료마다 살 수 있는 상품을 하나 붙인다 (없으면 product가 null)
     * @param missingSelectedIngredients 선택 재료(말한 재료 포함) 중 냉장고에 없는 것의 이름. 같은 종류면 레시피에 쓰인 이름.
     *                                   missingIngredients에도 들어 있다
     * @param lacksManyIngredients 재료를 많이 사야 하는지 (보유 비율 기준 미만)
     * @param reason               카드에 보여 줄 추천 이유 한 줄 (25자 안팎, 예: "버섯·두부가 주재료예요")
     * @param description          카드 아래 AI 설명. 추천 이벤트에서는 null(설명은 따로 흘러나옴), 메시지 조회에서는 저장된 값
     */
    public record RecommendedRecipe(
            Long recipeId,
            String name,
            String imageUrl,
            String category,
            Integer cookTimeMin,
            List<String> usedIngredients,
            List<ImminentIngredient> imminentIngredients,
            List<String> matchedTastes,
            List<MissingIngredient> missingIngredients,
            List<String> missingSelectedIngredients,
            boolean lacksManyIngredients,
            String reason,
            String description
    ) {
    }

    /**
     * @param dDay 오늘 기준 소비기한까지 남은 일수 (0이면 오늘까지)
     */
    public record ImminentIngredient(String name, long dDay) {
    }

    /**
     * @param product 이 재료로 담을 상품. 판매 상품이 없으면 null
     */
    public record MissingIngredient(Long ingredientId, String name, ShoppingProduct product) {

        public MissingIngredient(Long ingredientId, String name) {
            this(ingredientId, name, null);
        }
    }

    /**
     * 장볼 재료로 담을 상품. 가격·품절은 카드를 만들 때(다시 불러올 때도) 기준이다.
     *
     * @param price      정가(원)
     * @param salePrice  진행 중 할인의 판매가. 할인이 없으면 null
     * @param saleType   할인 구분 (SPECIAL·FLASH_DROP). 할인이 없으면 null
     * @param soldOut    지금 살 수 없는지 (상품 품절·번개 드롭 남은 수량 0)
     * @param unitAmount 상품 용량 수치 (예: 300)
     * @param unit       용량 단위 (g·개·단…)
     */
    public record ShoppingProduct(
            Long productId,
            String name,
            String imageUrl,
            int price,
            Integer salePrice,
            SaleEventType saleType,
            boolean soldOut,
            BigDecimal unitAmount,
            String unit
    ) {
    }
}

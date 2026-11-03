package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCandidateScorerTest {

    private static final Long MEMBER_ID = 1L;

    // 재료 ID
    private static final long EGG = 1L;
    private static final long TOFU = 2L;
    private static final long GARLIC = 3L;
    private static final long ONION = 4L;
    private static final long PORK = 5L;
    private static final long SALT = 90L;

    private final RecipeCandidateScorer scorer = new RecipeCandidateScorer(List.of("양념", "음료"));

    private final List<RecipeCandidateIngredient> rows = new ArrayList<>();
    private final Map<Long, RecipeCandidateInfo> infos = new HashMap<>();
    private final Map<Long, Set<Taste>> tastes = new HashMap<>();

    @Nested
    @DisplayName("정렬")
    class Ordering {

        @Test
        @DisplayName("선택 재료가 1개 겹친 레시피가 맛과 임박 재료가 모두 맞는 레시피보다 앞선다")
        void ranksSelectedFirst_overTasteAndImminent() {
            recipe(10L, "두부조림", "조림", 20, 5, TOFU);
            recipe(20L, "계란찜", "찜", 20, 1, EGG);
            tastes.put(20L, Set.of(Taste.MILD));

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of(TOFU)),
                    List.of(stock(EGG, "계란", 0), stock(TOFU, "두부", 10)),
                    List.of(Taste.MILD));

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
            assertThat(result.candidates().get(1).ranking().tasteMatches()).isEqualTo(1);
            assertThat(result.candidates().get(1).ranking().imminentScore()).isEqualTo(12);
        }

        @Test
        @DisplayName("임박 재료가 1개 들어간 레시피가 선호 맛이 2개 겹친 레시피보다 앞선다")
        void ranksImminentFirst_overTaste() {
            recipe(10L, "두부부침", "전", 20, 1, TOFU);
            recipe(20L, "계란찜", "찜", 20, 5, EGG);
            tastes.put(10L, Set.of(Taste.SALTY, Taste.SWEET));

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 3), stock(TOFU, "두부", 10)),
                    List.of(Taste.SALTY, Taste.SWEET));

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(0).ranking().imminentScore()).isEqualTo(6);
            assertThat(result.candidates().get(0).ranking().tasteMatches()).isZero();
            assertThat(result.candidates().get(1).ranking().tasteMatches()).isEqualTo(2);
        }

        @Test
        @DisplayName("임박 재료가 들어간 레시피가 보유 비율이 더 높은 레시피보다 앞선다")
        void ranksImminentFirst_overOwnedRatio() {
            recipe(10L, "계란국", "국", 20, 10, EGG);
            recipe(20L, "두부김치", "볶음", 20, 1, TOFU);

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 3), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
            assertThat(result.candidates().get(0).ranking().imminentScore()).isEqualTo(6);
            assertThat(result.candidates().get(1).ranking().ownedRatio()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("사용자 요청(포함 재료)이 맞은 레시피가 선택 재료가 겹친 레시피보다 앞선다")
        void ranksRequestFirst_overSelected() {
            recipe(10L, "두부조림", "조림", 20, 5, TOFU);
            recipe(20L, "제육볶음", "볶음", 20, 5, PORK);

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(TOFU), null, null, null, List.of(PORK), null);
            RecipeCandidates result = rank(query, List.of(stock(TOFU, "두부", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(0).reason().matchedRequests()).containsExactly("재료: 돼지고기");
        }

        @Test
        @DisplayName("임박 점수는 D-0 12, D-1 10, D-2 8, D-3 6으로 더하고 D-4부터는 0이다")
        void sumsImminentScoreByDday() {
            recipe(10L, "잡채", "볶음", 30, 6, EGG, TOFU, GARLIC, ONION, PORK);

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 0), stock(TOFU, "두부", 1), stock(GARLIC, "마늘", 2),
                            stock(ONION, "양파", 3), stock(PORK, "돼지고기", 4)),
                    List.of());

            RecipeCandidate candidate = result.candidates().get(0);
            assertThat(candidate.ranking().imminentScore()).isEqualTo(12 + 10 + 8 + 6);
            assertThat(candidate.reason().imminentIngredients()).containsExactly(
                    new ImminentIngredient("계란", 0), new ImminentIngredient("두부", 1),
                    new ImminentIngredient("마늘", 2), new ImminentIngredient("양파", 3));
            assertThat(candidate.reason().usedFridgeIngredients())
                    .containsExactly("계란", "두부", "마늘", "양파", "돼지고기");
        }

        @Test
        @DisplayName("흔한 재료(마늘·양파)만 겹친 레시피는 보유 비율이 낮아 뒤로 간다")
        void ranksCommonOnlyRecipeLast_byOwnedRatio() {
            recipe(10L, "갈비찜", "찜", 60, 10, GARLIC, ONION);
            recipe(20L, "두부부침", "전", 20, 2, TOFU);

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(GARLIC, "마늘", 10), stock(ONION, "양파", 10), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(1).ranking().ownedCount()).isEqualTo(2);
            assertThat(result.candidates().get(1).ranking().missingCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("보유 비율이 같으면 없는 재료가 적은 순, 그것도 같으면 레시피 id 순이다")
        void breaksTieByMissingCountThenRecipeId() {
            recipe(30L, "두부계란국", "국", 20, 4, EGG, TOFU);
            recipe(20L, "두부무침", "무침", 20, 2, TOFU);
            recipe(10L, "계란말이", "말이", 20, 2, EGG);

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L, 30L);
        }

        @Test
        @DisplayName("레시피 맛이 비어 있어도 맛 단계를 모두 0으로 두고 정렬한다")
        void ranksWithZeroTaste_whenRecipeTastesAreEmpty() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "두부조림", "조림", 20, 1, TOFU);

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 1)),
                    List.of(Taste.SALTY, Taste.SWEET));

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates()).allSatisfy(candidate -> {
                assertThat(candidate.ranking().tasteMatches()).isZero();
                assertThat(candidate.reason().matchedTastes()).isEmpty();
            });
        }

        @Test
        @DisplayName("후보가 10개를 넘으면 상위 10개만 돌려준다")
        void returnsTopTen_whenMoreCandidatesExist() {
            for (long id = 1; id <= 12; id++) {
                recipe(id, "계란요리" + id, "찜", 20, (int) id, EGG);
            }

            RecipeCandidates result = rank(RecipeQuery.of(MEMBER_ID, List.of()), List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates()).hasSize(10);
            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        }
    }

    @Nested
    @DisplayName("거르기")
    class Filtering {

        @Test
        @DisplayName("기본 양념만 겹치는 레시피는 후보에서 뺀다")
        void excludesRecipe_whenOnlyBasicSeasoningOverlaps() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            rows.add(new RecipeCandidateIngredient(20L, SALT, "소금", true));
            infos.put(20L, new RecipeCandidateInfo(20L, "소금구이", "구이", 20, 1));

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of(SALT)),
                    List.of(stock(EGG, "계란", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
        }

        @Test
        @DisplayName("제외 분류(양념·음료)의 레시피는 후보에서 뺀다")
        void excludesRecipe_whenCategoryIsExcluded() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "계란 양념장", "양념", 5, 1, EGG);
            recipe(30L, "에그노그", "음료", 5, 1, EGG);

            RecipeCandidates result = rank(RecipeQuery.of(MEMBER_ID, List.of()), List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
        }

        @Test
        @DisplayName("제외 재료가 하나라도 들어간 레시피는 후보에서 뺀다")
        void excludesRecipe_whenExcludeIngredientIsIncluded() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "제육계란말이", "말이", 20, 2, EGG, PORK);

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, List.of(PORK));
            RecipeCandidates result = rank(query, List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
        }

        @Test
        @DisplayName("최대 조리 시간은 시간이 있는 레시피만 거르고, 시간이 비어 있으면 통과시키고 근거에 표시한다")
        void passesRecipeWithoutCookTime_whenMaxMinutesIsGiven() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "계란장조림", "조림", 40, 1, EGG);
            recipe(30L, "계란국", "국", null, 1, EGG);

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, 30, null, null);
            RecipeCandidates result = rank(query, List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 30L);
            assertThat(result.candidates()).extracting(candidate -> candidate.reason().cookTimeUnknown())
                    .containsExactly(false, true);
        }
    }

    @Nested
    @DisplayName("냉장고 준비")
    class PreparingFridge {

        @Test
        @DisplayName("같은 재료가 여러 줄이면 가장 빠른 소비기한 하나로 합친다")
        void mergesSameIngredient_byEarliestExpiration() {
            List<FridgeStock> stocks = RecipeCandidateScorer.prepareFridge(List.of(
                    fridgeItem(EGG, "계란", 5, IngredientType.NORMAL, false),
                    fridgeItem(EGG, "계란", 1, IngredientType.NORMAL, false),
                    fridgeItem(TOFU, "두부", 3, IngredientType.NORMAL, false)
            ));

            assertThat(stocks).containsExactly(stock(EGG, "계란", 1), stock(TOFU, "두부", 3));
        }

        @Test
        @DisplayName("직접 입력·밀키트·기본 양념·기한 지난 재료는 뺀다")
        void excludesCustomMealkitBasicAndExpired() {
            List<FridgeStock> stocks = RecipeCandidateScorer.prepareFridge(List.of(
                    fridgeItem(100L, "할머니표 반찬", 5, IngredientType.CUSTOM, false),
                    fridgeItem(101L, "부대찌개 밀키트", 5, IngredientType.MEALKIT, false),
                    fridgeItem(SALT, "소금", 300, IngredientType.NORMAL, true),
                    fridgeItem(EGG, "계란", -1, IngredientType.NORMAL, false),
                    fridgeItem(TOFU, "두부", 0, IngredientType.NORMAL, false)
            ));

            assertThat(stocks).containsExactly(stock(TOFU, "두부", 0));
        }
    }

    @Nested
    @DisplayName("fallback")
    class Fallback {

        @Test
        @DisplayName("선택 재료가 없으면 fallback이다")
        void returnsFallback_whenNothingIsSelected() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);

            RecipeCandidates result = rank(RecipeQuery.of(MEMBER_ID, List.of()), List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.fallback()).isTrue();
        }

        @Test
        @DisplayName("선택 재료와 겹치는 레시피가 하나도 없으면 fallback이고 임박·보유 순으로 고른다")
        void returnsFallback_whenSelectedDoesNotOverlap() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "두부조림", "조림", 20, 1, TOFU);

            RecipeCandidates result = rank(
                    RecipeQuery.of(MEMBER_ID, List.of(PORK)),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 0)),
                    List.of());

            assertThat(result.fallback()).isTrue();
            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
        }

        @Test
        @DisplayName("선택 재료와 겹치는 레시피가 있으면 fallback이 아니다")
        void returnsNoFallback_whenSelectedOverlaps() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);

            RecipeCandidates result = rank(RecipeQuery.of(MEMBER_ID, List.of(EGG)), List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.fallback()).isFalse();
            assertThat(result.candidates().get(0).ranking().selectedMatches()).isEqualTo(1);
        }
    }

    private RecipeCandidates rank(RecipeQuery query, List<FridgeStock> fridge, List<Taste> memberTastes) {
        return scorer.rank(query, fridge, memberTastes, rows, infos, tastes);
    }

    // 레시피 하나를 등록한다. ingredientIds는 기본 양념이 아닌 재료이고, 이름은 재료 ID로 정한다
    private void recipe(Long recipeId, String name, String category, Integer cookTimeMin, int nonBasicCount,
                        long... ingredientIds) {
        for (long ingredientId : ingredientIds) {
            rows.add(new RecipeCandidateIngredient(recipeId, ingredientId, nameOf(ingredientId), false));
        }
        infos.put(recipeId, new RecipeCandidateInfo(recipeId, name, category, cookTimeMin, nonBasicCount));
    }

    private static String nameOf(long ingredientId) {
        return switch ((int) ingredientId) {
            case 1 -> "계란";
            case 2 -> "두부";
            case 3 -> "마늘";
            case 4 -> "양파";
            case 5 -> "돼지고기";
            default -> "재료" + ingredientId;
        };
    }

    private static FridgeStock stock(long ingredientId, String name, long dDay) {
        return new FridgeStock(ingredientId, name, dDay);
    }

    private static FridgeItemResponse fridgeItem(long ingredientId, String name, long dDay,
                                                 IngredientType type, boolean basicSeasoning) {
        FridgeItemStatus status = FridgeItemStatus.of(dDay);
        return new FridgeItemResponse(
                ingredientId * 10, ingredientId, name, "개", name, BigDecimal.ONE, StorageType.REFRIGERATED,
                LocalDate.now().plusDays(dDay), FridgeSource.MANUAL, null,
                status == FridgeItemStatus.IMMINENT, status == FridgeItemStatus.EXPIRED, dDay, status,
                type, basicSeasoning, LocalDateTime.now(), LocalDateTime.now());
    }
}

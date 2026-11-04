package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
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
    private static final long MUSHROOM = 45L;
    private static final long SHIITAKE = 161L;

    // 정렬·거르기 테스트는 보유 비율 기준 없이(0) 단계별 순서만 확인한다
    private final RecipeCandidateScorer scorer = new RecipeCandidateScorer(List.of("양념", "음료"), 0);
    private final RecipeCandidateScorer ratioScorer = new RecipeCandidateScorer(List.of("양념", "음료"), 0.4);

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
        @DisplayName("사용자 요청(분류)이 맞은 레시피가 선택 재료가 겹친 레시피보다 앞선다")
        void ranksRequestFirst_overSelected() {
            recipe(10L, "두부조림", "조림", 20, 5, TOFU);
            recipe(20L, "제육볶음", "볶음", 20, 5, PORK);

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(TOFU), null, List.of("볶음"), null, null, false);
            RecipeCandidates result = rank(query, List.of(stock(TOFU, "두부", 10), stock(PORK, "돼지고기", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(0).reason().matchedRequests()).containsExactly("분류: 볶음");
        }

        @Test
        @DisplayName("정렬 1단계(요청)는 원하는 맛과 분류만 세고, 선택 재료(말한 재료 포함)는 2단계에서만 센다")
        void countsOnlyTasteAndCategory_inRequestStep() {
            recipe(10L, "두부조림", "조림", 20, 1, TOFU);
            tastes.put(10L, Set.of(Taste.SPICY));

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(TOFU), List.of(Taste.SPICY), List.of("조림"), null, null, false);
            RecipeCandidates result = rank(query, List.of(stock(TOFU, "두부", 10)), List.of());

            RecipeCandidate candidate = result.candidates().get(0);
            assertThat(candidate.ranking().requestMatches()).isEqualTo(2);
            assertThat(candidate.ranking().selectedMatches()).isEqualTo(1);
            assertThat(candidate.reason().matchedRequests()).containsExactly("맛: SPICY", "분류: 조림");
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

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, List.of(PORK), false);
            RecipeCandidates result = rank(query, List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
        }

        @Test
        @DisplayName("최대 조리 시간은 시간이 있는 레시피만 거르고, 시간이 비어 있으면 통과시키고 근거에 표시한다")
        void passesRecipeWithoutCookTime_whenMaxMinutesIsGiven() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "계란장조림", "조림", 40, 1, EGG);
            recipe(30L, "계란국", "국", null, 1, EGG);

            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, 30, null, false);
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

    @Nested
    @DisplayName("보유 비율 기준")
    class OwnedRatioThreshold {

        @Test
        @DisplayName("보유 비율이 기준 이상인 레시피가 임박 점수가 더 높은 기준 미만 레시피보다 앞선다")
        void ranksEnoughOwnedFirst_overHigherImminent() {
            recipe(10L, "계란찜", "찜", 20, 5, EGG);
            recipe(20L, "두부부침", "전", 20, 2, TOFU);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 0), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(1).ranking().imminentScore()).isEqualTo(12);
            assertThat(result.candidates()).extracting(candidate -> candidate.reason().lacksManyIngredients())
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("선택 재료를 전부 쓰는 레시피는 보유 비율이 기준 미만이어도 앞에 두고 모자람 표시는 남긴다")
        void ranksAllSelectedRecipeFirst_whenOwnedRatioIsLow() {
            recipe(10L, "두부제육전골", "전골", 30, 10, TOFU, PORK);
            recipe(20L, "계란말이", "말이", 20, 2, EGG);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of(TOFU, PORK)),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10), stock(PORK, "돼지고기", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
            assertThat(result.candidates().get(0).ranking().selectedMatches()).isEqualTo(2);
            assertThat(result.candidates()).extracting(candidate -> candidate.reason().lacksManyIngredients())
                    .containsExactly(true, false);
        }

        @Test
        @DisplayName("선택 재료가 1개면 그 재료를 쓰는 레시피도 보유 비율이 기준 미만이면 뒤로 간다")
        void ranksSingleSelectedRecipeLast_whenOwnedRatioIsLow() {
            recipe(10L, "두부전골", "전골", 30, 10, TOFU);
            recipe(20L, "계란말이", "말이", 20, 2, EGG);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of(TOFU)),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(1).ranking().selectedMatches()).isEqualTo(1);
            assertThat(result.candidates().get(1).reason().lacksManyIngredients()).isTrue();
        }

        @Test
        @DisplayName("기본 양념을 섞어 선택해도 양념을 빼고 1개면 예외 없이 보유 비율 기준을 적용한다")
        void ranksSelectedRecipeLast_whenOnlyOneNonBasicIsSelectedWithSeasoning() {
            recipe(10L, "두부전골", "전골", 30, 10, TOFU);
            rows.add(new RecipeCandidateIngredient(10L, SALT, "소금", true));
            recipe(20L, "계란말이", "말이", 20, 2, EGG);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of(TOFU, SALT)),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
        }

        @Test
        @DisplayName("기본 양념을 섞어 선택해도 양념을 빼고 2개를 전부 쓰면 보유 비율이 기준 미만이어도 앞에 둔다")
        void ranksAllSelectedRecipeFirst_whenTwoNonBasicAreSelectedWithSeasoning() {
            recipe(10L, "두부제육전골", "전골", 30, 10, TOFU, PORK);
            rows.add(new RecipeCandidateIngredient(10L, SALT, "소금", true));
            recipe(20L, "계란말이", "말이", 20, 2, EGG);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of(TOFU, PORK, SALT)),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10), stock(PORK, "돼지고기", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
            assertThat(result.candidates().get(0).reason().lacksManyIngredients()).isTrue();
        }

        @Test
        @DisplayName("선택 재료를 일부만 쓰는 레시피는 보유 비율이 기준 미만이면 기준 이상 레시피보다 뒤로 간다")
        void ranksPartlySelectedRecipeLast_whenOwnedRatioIsLow() {
            recipe(10L, "두부전골", "전골", 30, 10, TOFU);
            recipe(20L, "계란말이", "말이", 20, 2, EGG);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of(TOFU, PORK)),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10), stock(PORK, "돼지고기", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(1).ranking().selectedMatches()).isEqualTo(1);
            assertThat(result.candidates().get(1).reason().lacksManyIngredients()).isTrue();
            assertThat(result.fallback()).isFalse();
        }

        @Test
        @DisplayName("기준 이상 레시피가 10개가 안 되면 기준 미만 레시피를 같은 정렬 순서로 채운다")
        void fillsWithLowOwnedRecipes_whenEnoughOwnedAreFewerThanTen() {
            for (long id = 1; id <= 8; id++) {
                recipe(id, "계란요리" + id, "찜", 20, 2, EGG);
            }
            recipe(31L, "두부요리 임박 없음", "찜", 20, 5, TOFU);
            recipe(32L, "돼지고기요리 D-1", "볶음", 20, 5, PORK);
            recipe(33L, "돼지고기요리 D-1 재료 많음", "볶음", 20, 9, PORK);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10), stock(PORK, "돼지고기", 1)),
                    List.of());

            assertThat(result.candidates()).hasSize(10);
            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 32L, 33L);
            assertThat(result.candidates()).extracting(candidate -> candidate.reason().lacksManyIngredients())
                    .containsExactly(false, false, false, false, false, false, false, false, true, true);
        }

        @Test
        @DisplayName("보유 비율이 기준과 같으면(2/5 = 0.4) 기준 이상으로 본다")
        void treatsAsEnough_whenOwnedRatioEqualsThreshold() {
            recipe(10L, "두부계란국", "국", 20, 5, EGG, TOFU);

            RecipeCandidates result = rank(ratioScorer,
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates().get(0).reason().lacksManyIngredients()).isFalse();
        }

        @Test
        @DisplayName("기준값이 0이면 보유 비율과 관계없이 정렬 순서대로 고르고 모자람 표시도 없다")
        void ranksByOrderOnly_whenThresholdIsZero() {
            recipe(10L, "계란찜", "찜", 20, 5, EGG);
            recipe(20L, "두부부침", "전", 20, 2, TOFU);

            RecipeCandidates result = rank(scorer,
                    RecipeQuery.of(MEMBER_ID, List.of()),
                    List.of(stock(EGG, "계란", 0), stock(TOFU, "두부", 10)),
                    List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
            assertThat(result.candidates()).extracting(candidate -> candidate.reason().lacksManyIngredients())
                    .containsExactly(false, false);
        }
    }

    @Nested
    @DisplayName("선택 재료만 쓰기")
    class SelectedOnly {

        @Test
        @DisplayName("선택 재료만 쓰기면 선택 재료를 하나도 쓰지 않는 레시피를 후보에서 뺀다")
        void excludesRecipeWithoutSelected_whenUseSelectedOnly() {
            recipe(10L, "두부조림", "조림", 20, 1, TOFU);
            recipe(20L, "계란찜", "찜", 20, 1, EGG);
            recipe(30L, "두부계란전", "전", 20, 2, TOFU, EGG);
            List<FridgeStock> fridge = List.of(stock(EGG, "계란", 0), stock(TOFU, "두부", 10));

            RecipeCandidates selectedOnly = rank(RecipeQuery.of(MEMBER_ID, List.of(TOFU), true), fridge, List.of());
            RecipeCandidates all = rank(RecipeQuery.of(MEMBER_ID, List.of(TOFU), false), fridge, List.of());

            assertThat(selectedOnly.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(10L, 30L);
            assertThat(all.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(10L, 20L, 30L);
        }

        @Test
        @DisplayName("선택 재료가 기본 양념뿐이면 선택 재료만 쓰기를 걸지 않는다")
        void keepsAllRecipes_whenSelectedIsOnlyBasicSeasoning() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            rows.add(new RecipeCandidateIngredient(20L, SALT, "소금", true));
            rows.add(new RecipeCandidateIngredient(20L, TOFU, "두부", false));
            infos.put(20L, new RecipeCandidateInfo(20L, "두부소금구이", "구이", 20, 1));

            RecipeCandidates result = rank(RecipeQuery.of(MEMBER_ID, List.of(SALT), true),
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(10L, 20L);
        }

        @Test
        @DisplayName("냉장고에 없는 선택 재료도 후보를 모으는 데 쓰고, 그 재료는 부족한 재료로 나온다")
        void collectsRecipeWithSelectedNotInFridge() {
            recipe(10L, "제육볶음", "볶음", 20, 2, PORK, ONION);
            recipe(20L, "계란찜", "찜", 20, 1, EGG);
            List<FridgeStock> fridge = List.of(stock(EGG, "계란", 10), stock(ONION, "양파", 10));

            RecipeCandidates ranked = rank(RecipeQuery.of(MEMBER_ID, List.of(PORK), true), fridge, List.of());
            RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(ranked, fridge, List.of(
                    new RecipeCandidateIngredient(10L, PORK, "돼지고기", false),
                    new RecipeCandidateIngredient(10L, ONION, "양파", false)));

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
            RecipeCandidate candidate = result.candidates().get(0);
            assertThat(candidate.ranking().selectedMatches()).isEqualTo(1);
            assertThat(candidate.ranking().ownedCount()).isEqualTo(1);
            assertThat(candidate.reason().missingIngredients()).containsExactly(new MissingIngredient(PORK, "돼지고기"));
        }
    }

    @Nested
    @DisplayName("같은 종류 묶음")
    class SameKindGroups {

        @Test
        @DisplayName("선택 재료(버섯)의 같은 종류 재료(표고버섯)를 쓰는 레시피도 후보로 모으고 선택 재료를 쓴 것으로 센다")
        void countsSelected_whenRecipeUsesSameKindIngredient() {
            recipe(10L, "표고버섯볶음", "볶음", 20, 1, SHIITAKE);
            recipe(20L, "계란찜", "찜", 20, 1, EGG);
            RecipeQuery query = RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM), true)
                    .withSelectedIngredientGroups(Map.of(MUSHROOM, Set.of(MUSHROOM, SHIITAKE)));

            RecipeCandidates result = rank(query, List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(scorer.lookupIngredientIds(query, List.of())).contains(MUSHROOM, SHIITAKE);
            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
            assertThat(result.candidates().get(0).ranking().selectedMatches()).isEqualTo(1);
            assertThat(result.fallback()).isFalse();
        }

        @Test
        @DisplayName("묶음이 없으면 선택 재료 자신만 센다")
        void countsOnlySelf_whenNoGroup() {
            recipe(10L, "표고버섯볶음", "볶음", 20, 1, SHIITAKE);
            recipe(20L, "버섯전", "전", 20, 1, MUSHROOM);

            RecipeCandidates result = rank(RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM), true),
                    List.of(stock(SHIITAKE, "표고버섯", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L);
        }

        @Test
        @DisplayName("선택 재료 2개를 같은 종류 재료로 모두 쓰면 보유 비율이 낮아도 먼저 고른다")
        void ranksFirstTier_whenUsesAllSelectedThroughGroup() {
            recipe(10L, "표고두부전골", "전골", 20, 10, SHIITAKE, TOFU);
            recipe(20L, "계란찜", "찜", 20, 1, EGG);
            RecipeQuery query = RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM, TOFU), false)
                    .withSelectedIngredientGroups(Map.of(MUSHROOM, Set.of(SHIITAKE)));

            RecipeCandidates result = rank(ratioScorer, query,
                    List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
            assertThat(result.candidates().get(0).ranking().selectedMatches()).isEqualTo(2);
            assertThat(result.candidates().get(0).reason().lacksManyIngredients()).isTrue();
        }
    }

    @Nested
    @DisplayName("냉장고에 없는 선택 재료")
    class MissingSelected {

        @Test
        @DisplayName("냉장고에 없는 선택 재료는 따로 표시하고, 부족한 재료·보유 비율에서는 없는 재료로 센다")
        void marksMissingSelected_andCountsAsMissing() {
            recipe(10L, "두부계란전", "전", 20, 2, TOFU, EGG);
            List<FridgeStock> fridge = List.of(stock(EGG, "계란", 10));

            RecipeCandidates ranked = rank(RecipeQuery.of(MEMBER_ID, List.of(TOFU), true), fridge, List.of());
            RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(ranked, fridge, List.of(
                    new RecipeCandidateIngredient(10L, TOFU, "두부", false),
                    new RecipeCandidateIngredient(10L, EGG, "계란", false)));

            RecipeCandidate candidate = result.candidates().get(0);
            assertThat(candidate.reason().missingSelectedIngredients()).containsExactly("두부");
            assertThat(candidate.reason().missingIngredients()).containsExactly(new MissingIngredient(TOFU, "두부"));
            assertThat(candidate.ranking().ownedCount()).isEqualTo(1);
            assertThat(candidate.ranking().ownedRatio()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("같은 종류로 넓힌 선택 재료는 레시피에 실제로 쓰인 재료 이름으로 표시한다 (버섯 → 표고버섯)")
        void marksRecipeIngredientName_whenSelectedByGroup() {
            recipe(10L, "표고버섯볶음", "볶음", 20, 1, SHIITAKE);
            rows.set(0, new RecipeCandidateIngredient(10L, SHIITAKE, "표고버섯", false));
            RecipeQuery query = RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM), true)
                    .withSelectedIngredientGroups(Map.of(MUSHROOM, Set.of(SHIITAKE)));

            RecipeCandidates result = rank(query, List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates().get(0).reason().missingSelectedIngredients()).containsExactly("표고버섯");
        }

        @Test
        @DisplayName("같은 종류 재료 중 레시피에 쓰인 것이 냉장고에 있으면 냉장고에 없는 선택 재료로 표시하지 않는다")
        void skipsMissingSelected_whenSameKindIsOwned() {
            recipe(10L, "표고버섯볶음", "볶음", 20, 1, SHIITAKE);
            RecipeQuery query = RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM), true)
                    .withSelectedIngredientGroups(Map.of(MUSHROOM, Set.of(SHIITAKE)));

            RecipeCandidates result = rank(query, List.of(stock(SHIITAKE, "표고버섯", 10)), List.of());

            assertThat(result.candidates().get(0).reason().missingSelectedIngredients()).isEmpty();
        }
    }

    @Nested
    @DisplayName("선택 재료가 있을 때 바로 만들 수 있는 레시피 먼저")
    class ReadyFirstWithSelected {

        @Test
        @DisplayName("선택 재료 겹친 수가 같으면 재료 많이 모자람이 아닌 레시피가 임박 점수가 낮아도 앞에 온다")
        void ranksReadyFirst_whenSelectedMatchesAreEqual() {
            recipe(10L, "두부제육전골", "전골", 20, 10, TOFU, ONION);
            recipe(20L, "두부조림", "조림", 20, 2, TOFU);
            List<FridgeStock> fridge = List.of(stock(TOFU, "두부", 10), stock(ONION, "양파", 0));

            RecipeCandidates result = rank(ratioScorer, RecipeQuery.of(MEMBER_ID, List.of(TOFU)), fridge, List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(1).ranking().imminentScore())
                    .isGreaterThan(result.candidates().get(0).ranking().imminentScore());
        }

        @Test
        @DisplayName("선택 재료 2개를 모두 쓰는 레시피끼리도 모자람이 아닌 레시피가 앞에 온다")
        void ranksReadyFirst_amongRecipesUsingAllSelected() {
            recipe(10L, "버섯두부전골", "전골", 20, 16, MUSHROOM, TOFU, ONION);
            recipe(20L, "버섯두부조림", "조림", 20, 3, MUSHROOM, TOFU);
            List<FridgeStock> fridge = List.of(stock(MUSHROOM, "버섯", 10), stock(TOFU, "두부", 10), stock(ONION, "양파", 0));

            RecipeCandidates result = rank(ratioScorer, RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM, TOFU)), fridge, List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
        }

        @Test
        @DisplayName("선택 재료가 없으면 지금처럼 임박 점수 순서를 따른다 (모자람은 보유 비율 1차·2차로만)")
        void keepsImminentOrder_whenNoSelected() {
            recipe(10L, "양파볶음", "볶음", 20, 2, ONION);
            recipe(20L, "두부조림", "조림", 20, 2, TOFU);
            List<FridgeStock> fridge = List.of(stock(TOFU, "두부", 10), stock(ONION, "양파", 0));

            RecipeCandidates result = rank(ratioScorer, RecipeQuery.of(MEMBER_ID, List.of()), fridge, List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
        }
    }

    @Nested
    @DisplayName("요리 이름 키워드")
    class KeywordMatches {

        @Test
        @DisplayName("키워드로 찾은 레시피는 냉장고 재료와 겹치지 않아도 후보에 넣고, 요청 일치에 키워드를 더한다")
        void collectsKeywordRecipe_andCountsAsRequest() {
            recipe(10L, "수제비", "면류", 20, 2, EGG);
            recipe(20L, "물냉면", "면류", 20, 8, PORK);
            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("면류"), null, null, false,
                    List.of("냉면"), "냉면").withKeywordMatches(Map.of(20L, KeywordMatch.of(KeywordMatch.Type.NAME)));

            RecipeCandidates result = rank(ratioScorer, query, List.of(stock(EGG, "계란", 10)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            RecipeCandidate coldNoodle = result.candidates().get(0);
            assertThat(coldNoodle.ranking().requestMatches()).isEqualTo(2);
            assertThat(coldNoodle.reason().matchedRequests()).containsExactly("키워드: 이름", "분류: 면류");
            assertThat(coldNoodle.reason().lacksManyIngredients()).isTrue();
        }

        @Test
        @DisplayName("키워드로 찾은 레시피는 재료를 많이 사야 해도 1차 후보로 둔다")
        void keepsKeywordRecipeInFirstTier_whenLacksMany() {
            recipe(10L, "계란국", "국", 20, 1, EGG);
            recipe(20L, "물냉면", "면류", 20, 8, PORK);
            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                    List.of("냉면"), "냉면").withKeywordMatches(Map.of(20L, KeywordMatch.of(KeywordMatch.Type.NAME)));

            RecipeCandidates result = rank(ratioScorer, query, List.of(stock(EGG, "계란", 0)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
        }

        @Test
        @DisplayName("벡터 검색으로만 찾거나 단어가 쪼개져 걸린 레시피는 후보에는 넣지만 키워드 일치·1차 후보로 치지 않는다")
        void skipsKeywordCredit_whenVectorOrSplit() {
            recipe(10L, "계란국", "국", 20, 1, EGG);
            recipe(20L, "들깨 냉채", "면류", 20, 8, PORK);
            recipe(30L, "비빔쌀국수", "면류", 20, 8, PORK);
            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                    List.of("냉면", "비빔국수"), "냉면").withKeywordMatches(Map.of(
                    20L, KeywordMatch.of(KeywordMatch.Type.VECTOR),
                    30L, KeywordMatch.of(KeywordMatch.Type.SPLIT)));

            RecipeCandidates result = rank(ratioScorer, query, List.of(stock(EGG, "계란", 0)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L, 30L);
            assertThat(result.candidates().get(1).ranking().requestMatches()).isZero();
            assertThat(result.candidates().get(1).reason().keywordMatch().type()).isEqualTo(KeywordMatch.Type.VECTOR);
            assertThat(query.keywordsNotFound()).isTrue();
        }
    }

    @Nested
    @DisplayName("이 상담에서 이미 추천한 레시피")
    class AlreadyRecommended {

        @Test
        @DisplayName("같은 조건으로 두 번 추천하면 두 번째는 이미 추천한 레시피를 뒤로 보내 다른 레시피가 위로 온다")
        void ranksNotYetRecommendedFirst() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "계란말이", "말이", 20, 1, EGG);
            recipe(30L, "계란국", "국", 20, 1, EGG);
            List<FridgeStock> fridge = List.of(stock(EGG, "계란", 0));

            RecipeCandidates first = rank(RecipeQuery.of(MEMBER_ID, List.of()), fridge, List.of());
            RecipeCandidates second = rank(RecipeQuery.of(MEMBER_ID, List.of()).withAlreadyRecommended(Set.of(10L, 20L)),
                    fridge, List.of());

            assertThat(first.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L, 30L);
            assertThat(second.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(30L, 10L, 20L);
        }

        @Test
        @DisplayName("요청 일치는 이미 추천했는지보다 앞선다")
        void keepsRequestFirst() {
            recipe(10L, "계란찜", "찜", 20, 1, EGG);
            recipe(20L, "계란국", "국", 20, 1, EGG);
            RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("찜"), null, null, false)
                    .withAlreadyRecommended(Set.of(10L));

            RecipeCandidates result = rank(query, List.of(stock(EGG, "계란", 0)), List.of());

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L, 20L);
        }
    }

    @Nested
    @DisplayName("부족한 재료")
    class MissingIngredients {

        @Test
        @DisplayName("냉장고에 없는 재료를 레시피에 나온 순서대로 근거에 채우고 기본 양념은 넣지 않는다")
        void fillsMissingIngredients_exceptOwnedAndBasic() {
            recipe(10L, "제육두부조림", "조림", 20, 3, TOFU);
            recipe(20L, "계란찜", "찜", 20, 1, EGG);
            List<FridgeStock> fridge = List.of(stock(EGG, "계란", 10), stock(TOFU, "두부", 10));
            RecipeCandidates ranked = rank(RecipeQuery.of(MEMBER_ID, List.of()), fridge, List.of());

            RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(ranked, fridge, List.of(
                    new RecipeCandidateIngredient(10L, PORK, "돼지고기", false),
                    new RecipeCandidateIngredient(10L, TOFU, "두부", false),
                    new RecipeCandidateIngredient(10L, ONION, "양파", false),
                    new RecipeCandidateIngredient(10L, SALT, "소금", true),
                    new RecipeCandidateIngredient(20L, EGG, "계란", false)));

            assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L);
            assertThat(result.candidates().get(0).reason().missingIngredients()).isEmpty();
            assertThat(result.candidates().get(1).reason().missingIngredients()).containsExactly(
                    new MissingIngredient(PORK, "돼지고기"), new MissingIngredient(ONION, "양파"));
            assertThat(result.candidates().get(1).ranking()).isEqualTo(ranked.candidates().get(1).ranking());
        }
    }

    private RecipeCandidates rank(RecipeQuery query, List<FridgeStock> fridge, List<Taste> memberTastes) {
        return rank(scorer, query, fridge, memberTastes);
    }

    private RecipeCandidates rank(RecipeCandidateScorer target, RecipeQuery query, List<FridgeStock> fridge,
                                  List<Taste> memberTastes) {
        return target.rank(query, fridge, memberTastes, rows, infos, tastes);
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

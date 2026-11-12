package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeTasteReader.TasteDetails;
import org.example.takecareofmyfridgebe.domain.chat.recommend.MealkitPicker.FridgeMealkits;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 정해진 레시피를 지금 냉장고·회원 선호 맛 기준으로 계산해 카드 설명 재료를 만든다 (거르기·정렬 없이).
 * 레시피 자세히·밀키트 고르기(ChatRecommendationFacade)와 지난 대화 카드 다시 만들기(ChatCardRebuilder)가 쓴다.
 * 여러 도메인(냉장고·레시피·상품·검색)을 조회해서 facade 층에 둔다.
 */
@Component
@RequiredArgsConstructor
public class RecipeCardDescriber {

    private final FridgeService fridgeService;
    private final MemberService memberService;
    private final RecipeService recipeService;
    private final RecipeTasteReader recipeTasteReader;
    private final ChatCandidateProperties chatCandidateProperties;

    /**
     * 정해진 레시피들을 지금 냉장고·회원 선호 맛 기준으로 계산한다 (거르기·정렬 없이). 저장된 추천 카드를 다시 보여 줄 때 쓴다.
     * 레시피 정보·재료·맛·냉장고·회원 조회는 레시피 수와 상관없이 한 번씩이다. 없는(지워진) 레시피는 결과에서 빠진다.
     *
     * @return 레시피 ID별 후보 (임박·쓰는 재료·장볼 재료·모자람이 채워짐)
     */
    public Map<Long, RecipeCandidate> describeRecipes(Long memberId, Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        List<FridgeStock> fridge = RecipeCandidateScorer.prepareFridge(
                fridgeService.getItems(memberId, FridgeSortType.EXPIRATION));
        return describe(memberId, fridge, recipeIds, recipeService.findTastesByRecipeIds(recipeIds));
    }

    /**
     * 밀키트 추천 재료: 밀키트에 연결된 레시피의 지금 냉장고 기준 계산(가진 재료 수)과 레시피 맛·매울 수 있는 레시피, 냉장고에 있는 밀키트.
     * 맛·매운 정도는 후보와 같은 기준으로 ES에서 id로 읽고, ES를 쓰지 않거나 읽지 못한 레시피는 DB 맛 데이터·재료 이름으로 계산한다.
     * 냉장고 조회는 한 번이고, 레시피가 없으면 레시피 조회를 하지 않는다.
     */
    public MealkitRecipes describeMealkitRecipes(Long memberId, Collection<Long> recipeIds) {
        List<FridgeItemResponse> items = fridgeService.getItems(memberId, FridgeSortType.EXPIRATION);
        FridgeMealkits fridgeMealkits = toFridgeMealkits(items);
        if (recipeIds.isEmpty()) {
            return new MealkitRecipes(Map.of(), Map.of(), Set.of(), fridgeMealkits);
        }
        TasteDetails tastes = recipeTasteReader.readForMealkits(recipeIds, memberId);
        Map<Long, RecipeCandidate> recipes = describe(memberId, RecipeCandidateScorer.prepareFridge(items), recipeIds,
                tastes.tastes());
        return new MealkitRecipes(recipes, tastes.tastes(), tastes.spicy().maybe(), fridgeMealkits);
    }

    // 냉장고에 있는 밀키트 (저장된 밀키트 카드를 다시 보여 줄 때도 지금 냉장고 기준으로 쓴다). 냉장고 조회 1번
    public FridgeMealkits findFridgeMealkits(Long memberId) {
        return toFridgeMealkits(fridgeService.getItems(memberId, FridgeSortType.EXPIRATION));
    }

    /**
     * 냉장고 밀키트 줄을 상품 ID(fridge.product_id)로, 상품 ID가 없는 예전 줄은 등록 이름(raw_name)으로 묶는다.
     * 기한이 지난 줄은 뺀다. 같은 밀키트가 여러 줄이면 소비기한이 가장 가까운 줄의 남은 일수를 둔다.
     */
    private static FridgeMealkits toFridgeMealkits(List<FridgeItemResponse> items) {
        Map<Long, Long> byProduct = new HashMap<>();
        Map<String, Long> byName = new HashMap<>();
        for (FridgeItemResponse item : items) {
            if (item.ingredientType() != IngredientType.MEALKIT || item.dDay() < 0) {
                continue;
            }
            if (item.productId() != null) {
                byProduct.merge(item.productId(), item.dDay(), Math::min);
            } else if (item.rawName() != null) {
                byName.merge(ChatTexts.removeWhitespace(item.rawName()), item.dDay(), Math::min);
            }
        }
        return new FridgeMealkits(byProduct, byName);
    }

    /**
     * @param recipes             레시피 ID별 지금 냉장고 기준 계산 (지워진 레시피는 없다)
     * @param recipeTastes        레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
     * @param maybeSpicyRecipeIds 매울 수 있는 레시피 ID (매운 정도 MILD·HOT)
     * @param fridgeMealkits      냉장고에 있는 밀키트 (기한 지난 줄 제외)
     */
    public record MealkitRecipes(
            Map<Long, RecipeCandidate> recipes,
            Map<Long, Set<Member.Taste>> recipeTastes,
            Set<Long> maybeSpicyRecipeIds,
            FridgeMealkits fridgeMealkits
    ) {
    }

    private Map<Long, RecipeCandidate> describe(Long memberId, List<FridgeStock> fridge, Collection<Long> recipeIds,
                                                Map<Long, Set<Member.Taste>> recipeTastes) {
        RecipeCandidateScorer scorer = new RecipeCandidateScorer(
                chatCandidateProperties.excludedCategories(), chatCandidateProperties.minOwnedRatio());
        List<Member.Taste> memberTastes = memberService.getMyInfo(memberId).tastes();
        List<RecipeCandidateIngredient> rows = recipeService.findNonBasicIngredients(recipeIds);
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);

        List<RecipeCandidate> scored = scorer.scoreAll(memberId, fridge, memberTastes, rows, infos, recipeTastes, recipeIds);
        RecipeCandidates withMissing = RecipeCandidateScorer.withMissingIngredients(
                new RecipeCandidates(scored, false), fridge, rows);
        Map<Long, RecipeCandidate> byId = new LinkedHashMap<>();
        withMissing.candidates().forEach(candidate -> byId.put(candidate.recipeId(), candidate));
        return byId;
    }
}

package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 상담 추천에 쓸 레시피 후보를 고른다 (AI 없이 규칙으로).
 * 냉장고·회원·레시피 서비스에서 필요한 값을 모으고, 거르기·정렬은 RecipeCandidateScorer에 맡긴다.
 * 선택 재료는 재료 서비스로 같은 종류 재료를 찾아 묶는다 (예: 버섯 → 표고버섯·양송이버섯, 후보 검색에서만).
 */
@Component
@RequiredArgsConstructor
public class RecipeCandidateFacade {

    private final FridgeService fridgeService;
    private final MemberService memberService;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;
    private final ChatCandidateProperties chatCandidateProperties;

    /**
     * 회원 냉장고와 선택 재료·선호 맛·임박 재료로 레시피 후보를 최대 10개 고른다.
     *
     * @param query 회원 ID, 선택 재료, (나중에 AI가 채울) 요청 조건
     * @return 정렬된 후보와 fallback 여부 (선택 재료가 없거나 하나도 겹치지 않으면 true)
     */
    public RecipeCandidates findCandidates(RecipeQuery request) {
        RecipeQuery query = withSelectedIngredientGroups(request);
        RecipeCandidateScorer scorer = new RecipeCandidateScorer(
                chatCandidateProperties.excludedCategories(), chatCandidateProperties.minOwnedRatio());

        List<FridgeStock> fridge = RecipeCandidateScorer.prepareFridge(
                fridgeService.getItems(query.memberId(), FridgeSortType.EXPIRATION));
        // 원하는 맛을 말했으면 회원 선호 맛은 쓰지 않는다 (원하는 맛은 1단계 요청에서만 센다)
        // 회원 도메인에 맛만 조회하는 메서드가 없어 내 정보 조회를 쓴다
        List<Member.Taste> memberTastes = query.wantedTastes().isEmpty()
                ? memberService.getMyInfo(query.memberId()).tastes()
                : List.of();

        List<RecipeCandidateIngredient> rows =
                recipeService.findCandidateIngredients(scorer.lookupIngredientIds(query, fridge));
        Set<Long> recipeIds = scorer.candidateRecipeIds(query, fridge, rows);
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);
        Map<Long, Set<Member.Taste>> recipeTastes = recipeService.findTastesByRecipeIds(recipeIds);

        RecipeCandidates ranked = scorer.rank(query, fridge, memberTastes, rows, infos, recipeTastes);

        // 부족한 재료는 고른 10개만 조회한다
        List<Long> topRecipeIds = ranked.candidates().stream().map(RecipeCandidate::recipeId).toList();
        return RecipeCandidateScorer.withMissingIngredients(
                ranked, fridge, recipeService.findNonBasicIngredients(topRecipeIds));
    }

    // 선택 재료마다 이름이 그 이름으로 끝나는 같은 종류 재료를 묶는다. 이름을 모르는 ID는 자신만 쓴다
    private RecipeQuery withSelectedIngredientGroups(RecipeQuery query) {
        if (query.selectedIngredientIds().isEmpty()) {
            return query;
        }
        Map<Long, Ingredient> selected = ingredientService.getIngredientsByIds(query.selectedIngredientIds());
        Map<String, List<Long>> sameKindByName = ingredientService.findSameKindIdsByNames(
                selected.values().stream().map(Ingredient::getName).toList());

        Map<Long, Set<Long>> groups = new HashMap<>();
        selected.forEach((id, ingredient) -> groups.put(id,
                new HashSet<>(sameKindByName.getOrDefault(ingredient.getName().trim(), List.of()))));
        return query.withSelectedIngredientGroups(groups);
    }
}

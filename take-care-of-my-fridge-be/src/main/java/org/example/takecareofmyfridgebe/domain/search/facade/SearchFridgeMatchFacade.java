package org.example.takecareofmyfridgebe.domain.search.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeIngredientInfo;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchResponse;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchResponse.RecipeMatch;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 통합검색 결과를 로그인 회원의 냉장고와 비교한다.
 * <br>
 * 재료는 표준 재료 ID로 비교한다(냉장고 "국산 흙대파"와 레시피 "대파"가 같은 재료).
 * 기본 양념·직접 입력·밀키트 재료는 요리 완료 차감과 같은 기준(IngredientService)으로 세지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SearchFridgeMatchFacade {

    private final RecipeService recipeService;
    private final FridgeService fridgeService;
    private final IngredientService ingredientService;

    public SearchFridgeMatchResponse match(Long memberId, SearchFridgeMatchRequest request) {
        List<FridgeIngredientInfo> fridgeItems = fridgeService.findUsableIngredients(memberId);
        Set<Long> ownedIds = fridgeItems.stream()
                .map(FridgeIngredientInfo::ingredientId)
                .collect(Collectors.toSet());

        Map<Long, Set<Long>> ingredientIdsByRecipe = recipeService.findIngredientIds(request.recipeIds());
        Set<Long> excludedIds = ingredientService.getExcludedIngredientIds(allIds(ingredientIdsByRecipe.values()));

        List<RecipeMatch> recipes = request.recipeIds().stream()
                .map(recipeId -> {
                    Set<Long> counted = new HashSet<>(ingredientIdsByRecipe.getOrDefault(recipeId, Set.of()));
                    counted.removeAll(excludedIds);
                    int ingredientCount = counted.size();
                    counted.retainAll(ownedIds);
                    return new RecipeMatch(recipeId, counted.size(), ingredientCount);
                })
                .toList();

        return new SearchFridgeMatchResponse(recipes, keywordItems(fridgeItems, request.keyword()));
    }

    private static Set<Long> allIds(Collection<Set<Long>> idSets) {
        return idSets.stream().flatMap(Set::stream).collect(Collectors.toSet());
    }

    // 입력한 이름에 검색어가 들었거나 표준 재료 이름이 검색어와 같은 재료 ("대파" → '국산 흙대파'). 같은 이름은 한 번만
    private static List<String> keywordItems(List<FridgeIngredientInfo> fridgeItems, String keyword) {
        if (keyword == null) {
            return List.of();
        }
        return fridgeItems.stream()
                .filter(item -> item.rawName().contains(keyword) || keyword.equals(item.ingredientName()))
                .map(FridgeIngredientInfo::rawName)
                .distinct()
                .toList();
    }
}

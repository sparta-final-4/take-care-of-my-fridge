package org.example.takecareofmyfridgebe.domain.ingredient.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    /**
     * 재료 ID 목록 중 요리 차감에서 제외해야 할 재료 ID(기본 양념, 직접 입력 ING_CUSTOM, 밀키트 ING_MEALKIT) 목록을 조회한다.
     *
     * @param ingredientIds 검사할 재료 ID 컬렉션
     * @return 차감 제외 대상 재료 ID Set
     */
    public Set<Long> getExcludedIngredientIds(Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Collections.emptySet();
        }
        return ingredientRepository.findAllById(ingredientIds).stream()
                .filter(this::isExcludedFromCookingDeduction)
                .map(Ingredient::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 재료 ID 목록 중 기본 양념(is_basic_seasoning = true)인 재료 ID 목록을 조회한다.
     *
     * @param ingredientIds 검사할 재료 ID 컬렉션
     * @return 기본 양념 재료 ID Set
     */
    public Set<Long> findBasicSeasoningIds(Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Collections.emptySet();
        }
        return ingredientRepository.findAllById(ingredientIds).stream()
                .filter(Ingredient::isBasicSeasoning)
                .map(Ingredient::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 재료 ID 목록으로 재료를 한 번에 조회한다. 없는 ID는 결과에서 빠진다.
     *
     * @param ingredientIds 조회할 재료 ID 컬렉션
     * @return 재료 ID를 키로 하는 재료 맵
     */
    public Map<Long, Ingredient> getIngredientsByIds(Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ingredientRepository.findAllById(ingredientIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
    }

    /**
     * 밀키트 상품을 냉장고에 넣을 때 매핑하는 밀키트용 기본 재료(ING_MEALKIT)를 조회한다.
     *
     * @return 밀키트용 기본 재료
     * @throws BusinessException DB에 ING_MEALKIT 재료 행이 설정되어 있지 않은 경우
     */
    public Ingredient getMealkitIngredient() {
        return ingredientRepository.findByIngredientCode(Ingredient.ING_MEALKIT)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEALKIT_INGREDIENT_NOT_CONFIGURED));
    }

    private boolean isExcludedFromCookingDeduction(Ingredient ingredient) {
        return ingredient.isBasicSeasoning() || ingredient.isCustom() || ingredient.isMealkit();
    }
}

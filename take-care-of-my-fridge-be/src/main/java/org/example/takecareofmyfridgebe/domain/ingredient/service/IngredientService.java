package org.example.takecareofmyfridgebe.domain.ingredient.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.config.IngredientMatchProperties;
import org.example.takecareofmyfridgebe.domain.ingredient.dto.IngredientIndexSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientMatchProperties matchProperties;

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
     * 다른 도메인(chat 레시피 후보 검색)에서 쓰는 조회.
     * 이름마다 "이름이 그 이름으로 끝나는 재료"를 같은 종류로 묶어 ID를 돌려준다 (예: 버섯 → 버섯, 표고버섯, 양송이버섯).
     * 한국어 재료명은 종류가 끝에 오므로 끝부분이 같은 재료를 같은 종류로 본다 (IngredientMatcher의 끝부분 규칙과 같은 기준).
     * 이름이 suffix-min-length(공백 제외 2글자)보다 짧으면("파", "무") 너무 많은 재료와 겹치므로 이름이 같은 재료만 묶는다.
     * 직접 입력(ING_CUSTOM)·밀키트(ING_MEALKIT)는 빼고, 같은 이름의 재료가 없으면 빈 목록이다.
     * 냉장고 등록 매칭(IngredientMatcher)에는 쓰지 않는다.
     *
     * @param names 재료 이름 목록 (앞뒤 공백은 지운다)
     * @return 이름(정리한 것)을 키로 하는 같은 종류 재료 ID (ID 오름차순)
     */
    public Map<String, List<Long>> findSameKindIdsByNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<Long>> result = new LinkedHashMap<>();
        names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(name -> result.put(name, findSameKind(name).stream()
                        .filter(ingredient -> !ingredient.isCustom() && !ingredient.isMealkit())
                        .map(Ingredient::getId)
                        .sorted()
                        .toList()));
        return result;
    }

    /**
     * 다른 도메인(chat 알레르기 재료 빼기)에서 쓰는 조회.
     * 이름마다 "이름에 그 이름이 들어 있는 재료"를 돌려준다 (예: 땅콩 → 땅콩, 땅콩가루, 땅콩버터 / 새우 → 건새우, 새우젓).
     * 이름이 suffix-min-length(공백 제외 2글자)보다 짧으면("게", "잣") 들어 있는 것으로 보면 너무 많은 재료와 겹치므로("바게트", "스파게티")
     * 이름이 그 이름으로 시작하는 재료만 돌려준다 (게 → 게, 게살, 게맛살 / 잣 → 잣, 잣가루).
     * 직접 입력(ING_CUSTOM)·밀키트(ING_MEALKIT)는 빼고, 그런 재료가 없으면 빈 목록이다.
     *
     * @param names 재료 이름 목록 (앞뒤 공백은 지운다)
     * @return 이름(정리한 것)을 키로 하는 재료 (ID 오름차순)
     */
    public Map<String, List<Ingredient>> findContainingNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<Ingredient>> result = new LinkedHashMap<>();
        names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(name -> result.put(name, findContaining(name).stream()
                        .filter(ingredient -> !ingredient.isCustom() && !ingredient.isMealkit())
                        .sorted(Comparator.comparing(Ingredient::getId))
                        .toList()));
        return result;
    }

    private List<Ingredient> findContaining(String name) {
        if (name.replace(" ", "").length() < matchProperties.suffixMinLength()) {
            return ingredientRepository.findByNameStartingWith(name);
        }
        return ingredientRepository.findByNameContaining(name);
    }

    private List<Ingredient> findSameKind(String name) {
        if (name.replace(" ", "").length() < matchProperties.suffixMinLength()) {
            return ingredientRepository.findByNameIn(List.of(name));
        }
        return ingredientRepository.findByNameEndingWith(name);
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

    /**
     * ES 재료 색인용 전체 조회. 직접 입력(ING_CUSTOM)과 밀키트(ING_MEALKIT)는 이름 정제 대상이 아니므로 뺀다.
     */
    public List<IngredientIndexSource> findIndexSources() {
        return ingredientRepository.findAll().stream()
                .filter(this::isIndexTarget)
                .map(IngredientIndexSource::from)
                .toList();
    }

    // 단건 색인용: 없거나 색인 대상이 아니면 빈 Optional
    public Optional<IngredientIndexSource> findIndexSource(Long ingredientId) {
        return ingredientRepository.findById(ingredientId)
                .filter(this::isIndexTarget)
                .map(IngredientIndexSource::from);
    }

    private boolean isIndexTarget(Ingredient ingredient) {
        return !ingredient.isCustom() && !ingredient.isMealkit();
    }

    private boolean isExcludedFromCookingDeduction(Ingredient ingredient) {
        return ingredient.isBasicSeasoning() || ingredient.isCustom() || ingredient.isMealkit();
    }
}

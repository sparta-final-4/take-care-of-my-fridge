package org.example.takecareofmyfridgebe.domain.recipe.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCreateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdatedAtDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.example.takecareofmyfridgebe.domain.recipe.event.RecipeChangedEvent;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeIngredientRepository;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeRepository;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeStepRepository;
import org.example.takecareofmyfridgebe.global.config.CacheNames;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private static final String CLASSIFICATIONS_KEY = "'all'";

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientService ingredientService;
    private final ApplicationEventPublisher eventPublisher;

    // 정렬은 request.sort()로 저장소가 적용하므로 PageRequest에는 넣지 않는다.
    public PageResponse<RecipeListItemResponse> getRecipes(RecipeListRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());

        Page<RecipeListItemResponse> response = recipeRepository.findRecipesByCondition(request, pageable)
                .map(RecipeListItemResponse::from);

        return PageResponse.of(response);
    }

    // 분류 값은 레시피 등록, 수정 때만 바뀌므로 캐시하고, 그때 비운다.
    @Cacheable(cacheNames = CacheNames.RECIPE_CLASSIFICATIONS, key = CLASSIFICATIONS_KEY)
    public RecipeClassificationResponse getClassifications() {
        return new RecipeClassificationResponse(
                recipeRepository.findDistinctCuisineTypes(),
                recipeRepository.findDistinctCategories()
        );
    }

    public RecipeDetailResponse getRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));

        return toDetailResponse(
                recipe,
                recipeIngredientRepository.findByRecipeIdOrderBySeqAsc(recipe.getId()),
                recipeStepRepository.findByRecipeIdOrderByStepNoAsc(recipe.getId())
        );
    }

    // 레시피, 재료, 조리 단계를 한 트랜잭션으로 저장한다.
    @Transactional
    @CacheEvict(cacheNames = CacheNames.RECIPE_CLASSIFICATIONS, key = CLASSIFICATIONS_KEY)
    public RecipeDetailResponse createRecipe(RecipeCreateRequest request) {
        validateIngredientsExist(request.ingredientIds());

        Recipe recipe = recipeRepository.save(request.toEntity(newSourceId()));
        List<RecipeIngredient> ingredients = recipeIngredientRepository.saveAll(request.toIngredients(recipe));
        List<RecipeStep> steps = recipeStepRepository.saveAll(request.toSteps(recipe));

        // 단건 인덱싱 위한 이벤트 발행
        eventPublisher.publishEvent(RecipeChangedEvent.from(recipe.getId()));

        return toDetailResponse(recipe, ingredients, steps);
    }

    // 기본 정보는 보낸 필드만 바꾸고, 재료와 조리 단계는 보낸 경우에만 목록 전체를 교체한다.
    @Transactional
    @CacheEvict(cacheNames = CacheNames.RECIPE_CLASSIFICATIONS, key = CLASSIFICATIONS_KEY)
    public RecipeDetailResponse updateRecipe(Long recipeId, RecipeUpdateRequest request) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
        if (request.replacesIngredients()) {
            validateIngredientsExist(request.ingredientIds());
        }

        request.applyTo(recipe);

        RecipeDetailResponse response = toDetailResponse(recipe, updateIngredients(recipe, request), updateSteps(recipe, request));

        // 단건 인덱싱 위한 이벤트 발행
        eventPublisher.publishEvent(RecipeChangedEvent.from(recipe.getId()));

        return response;
    }

    private RecipeDetailResponse toDetailResponse(
            Recipe recipe,
            List<RecipeIngredient> ingredients,
            List<RecipeStep> steps
    ) {
        List<Long> ingredientIds = ingredients.stream()
                .map(RecipeIngredient::getIngredientId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> basicSeasoningIds = ingredientService.findBasicSeasoningIds(ingredientIds);

        return RecipeDetailResponse.of(recipe, ingredients, steps, basicSeasoningIds);
    }

    private List<RecipeIngredient> updateIngredients(Recipe recipe, RecipeUpdateRequest request) {
        if (!request.replacesIngredients()) {
            return recipeIngredientRepository.findByRecipeIdOrderBySeqAsc(recipe.getId());
        }
        recipeIngredientRepository.deleteAllByRecipeId(recipe.getId());
        return recipeIngredientRepository.saveAll(request.toIngredients(recipe));
    }

    private List<RecipeStep> updateSteps(Recipe recipe, RecipeUpdateRequest request) {
        if (!request.replacesSteps()) {
            return recipeStepRepository.findByRecipeIdOrderByStepNoAsc(recipe.getId());
        }
        recipeStepRepository.deleteAllByRecipeId(recipe.getId());
        return recipeStepRepository.saveAll(request.toSteps(recipe));
    }

    // recipe_ingredients.ingredient_id는 FK 없이 ID로만 참조하므로 저장 전에 직접 확인한다.
    private void validateIngredientsExist(List<Long> ingredientIds) {
        if (ingredientRepository.findAllById(ingredientIds).size() != ingredientIds.size()) {
            throw new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND);
        }
    }

    // (source, source_id)가 유니크라서 관리자 등록 레시피에도 겹치지 않는 원본 코드가 필요하다. 컬럼 길이(20자)에 맞춘다.
    private static String newSourceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }


    public PageResponse<RecipeListItemResponse> getRecipesByIds(List<Long> recipeIds, long total, RecipeListRequest request) {
        Map<Long, Recipe> recipesById = recipeRepository.findAllById(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));

        List<RecipeListItemResponse> content = recipeIds.stream()
                .map(recipesById::get)
                .filter(Objects::nonNull)
                .map(RecipeListItemResponse::from)
                .toList();

        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());
        return PageResponse.of(new PageImpl<>(content, pageable, total));
    }

    /**
     * 다른 도메인(review, cooking)에서 쓰는 조회.
     * 레시피 존재 여부를 확인한다.
     */
    public boolean existsById(Long recipeId) {
        if (recipeId == null) {
            return false;
        }
        return recipeRepository.existsById(recipeId);
    }

    /**
     * 다른 도메인(review, cooking)에서 쓰는 조회.
     * ID 목록에 해당하는 레시피의 요약 정보(이름, 이미지)를 한 번의 쿼리로 조회한다.
     * 비어 있으면 빈 Map을 반환하고, 없는 ID는 Map에서 빠진다.
     */
    public Map<Long, RecipeSummary> findSummaries(Collection<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = recipeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return recipeRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(
                        Recipe::getId,
                        recipe -> new RecipeSummary(recipe.getId(), recipe.getName(), recipe.getImageUrl(), recipe.getTipSummary()),
                        (existing, replacing) -> existing
                ));
    }

    /**
     * 레시피의 후기 요약(tipSummary)을 변경한다.
     * 요약이 기존과 같더라도 수정 시각(updated_at)이 갱신되어 새벽 재작업이 중복 실행되지 않도록
     * 엔티티 수정 대신 직접 UPDATE 쿼리를 실행한다.
     *
     * @param recipeId 레시피 ID
     * @param summary  변경할 후기 요약 내용
     * @throws IllegalArgumentException 요약 내용이 null이거나 비어 있는 경우
     * @throws BusinessException        레시피가 존재하지 않는 경우 (RECIPE_NOT_FOUND)
     */
    @Transactional
    public void updateTipSummary(Long recipeId, String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("후기 요약은 비어 있을 수 없습니다.");
        }

        int updatedCount = recipeRepository.updateTipSummary(recipeId, summary, LocalDateTime.now());
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
    }

    /**
     * 레시피 ID 목록의 최근 수정 시각(updated_at)을 한 번의 쿼리로 조회한다.
     * updated_at은 레시피 수정 때도 바뀌어서 요약 시각과 완전히 같지는 않다.
     * 새벽 작업이 "다시 요약할 레시피"를 고를 때 사용한다.
     *
     * @param recipeIds 레시피 ID 목록
     * @return 레시피 ID를 키로 하고 최근 수정 시각을 값으로 하는 Map
     */
    public Map<Long, LocalDateTime> findUpdatedAts(Collection<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = recipeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return recipeRepository.findUpdatedAtsByIdIn(distinctIds).stream()
                .collect(Collectors.toMap(
                        RecipeUpdatedAtDto::id,
                        RecipeUpdatedAtDto::updatedAt,
                        (existing, replacing) -> existing
                ));
    }
}

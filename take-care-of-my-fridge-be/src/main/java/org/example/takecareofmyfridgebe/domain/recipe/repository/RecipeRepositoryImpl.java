package org.example.takecareofmyfridgebe.domain.recipe.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientCountDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientNameDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientTextDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepImageDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepTextDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipeStep;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.ingredient.entity.QIngredient.ingredient;
import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipe.recipe;
import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipeIngredient.recipeIngredient;
import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipeStep.recipeStep;

@Repository
@RequiredArgsConstructor
public class RecipeRepositoryImpl implements RecipeRepositoryCustom {

    private static final char LIKE_ESCAPE = '\\';

    private final JPAQueryFactory queryFactory;

    // 비어 있는 조건(null)은 QueryDSL where에서 무시된다.
    @Override
    public Page<Recipe> findRecipesByCondition(RecipeListRequest condition, Pageable pageable) {
        BooleanExpression[] where = {
                nameContains(condition.keyword()),
                cuisineTypeEq(condition.cuisineType()),
                categoryEq(condition.category()),
                cookTimeLoe(condition.maxCookTimeMin()),
                difficultyLoe(condition.maxDifficulty())
        };

        List<Recipe> content = queryFactory
                .selectFrom(recipe)
                .where(where)
                .orderBy(orderSpecifiers(condition.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(recipe.count())
                .from(recipe)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<String> findDistinctCuisineTypes() {
        return findDistinctValues(recipe.cuisineType);
    }

    @Override
    public List<String> findDistinctCategories() {
        return findDistinctValues(recipe.category);
    }

    @Override
    public List<RecipeIngredientNameDto> findIngredientNames(Long recipeId) {
        return queryFactory
                .select(Projections.constructor(RecipeIngredientNameDto.class,
                        recipeIngredient.recipe.id,
                        recipeIngredient.ingredientId,
                        ingredient.name,
                        recipeIngredient.displayName,
                        recipeIngredient.role,
                        ingredient.basicSeasoning
                ))
                .from(recipeIngredient)
                .join(ingredient).on(ingredient.id.eq(recipeIngredient.ingredientId))
                .where(recipeIdEq(recipeIngredient.recipe.id, recipeId))
                .orderBy(recipeIngredient.recipe.id.asc(), recipeIngredient.seq.asc())
                .fetch();
    }

    @Override
    public List<RecipeCandidateIngredient> findCandidateIngredients(Collection<Long> ingredientIds) {
        return queryFactory
                .select(Projections.constructor(RecipeCandidateIngredient.class,
                        recipeIngredient.recipe.id,
                        recipeIngredient.ingredientId,
                        ingredient.name,
                        ingredient.basicSeasoning
                ))
                .distinct()
                .from(recipeIngredient)
                .join(ingredient).on(ingredient.id.eq(recipeIngredient.ingredientId))
                .where(recipeIngredient.ingredientId.in(ingredientIds))
                .fetch();
    }

    @Override
    public List<RecipeIngredientCountDto> countNonBasicIngredients(Collection<Long> recipeIds) {
        return queryFactory
                .select(Projections.constructor(RecipeIngredientCountDto.class,
                        recipeIngredient.recipe.id,
                        recipeIngredient.ingredientId.countDistinct()
                ))
                .from(recipeIngredient)
                .join(ingredient).on(ingredient.id.eq(recipeIngredient.ingredientId))
                .where(
                        recipeIngredient.recipe.id.in(recipeIds),
                        ingredient.basicSeasoning.isFalse()
                )
                .groupBy(recipeIngredient.recipe.id)
                .fetch();
    }

    @Override
    public List<RecipeCandidateIngredient> findNonBasicIngredients(Collection<Long> recipeIds) {
        return queryFactory
                .select(Projections.constructor(RecipeCandidateIngredient.class,
                        recipeIngredient.recipe.id,
                        recipeIngredient.ingredientId,
                        ingredient.name,
                        ingredient.basicSeasoning
                ))
                .from(recipeIngredient)
                .join(ingredient).on(ingredient.id.eq(recipeIngredient.ingredientId))
                .where(
                        recipeIngredient.recipe.id.in(recipeIds),
                        ingredient.basicSeasoning.isFalse()
                )
                // 같은 재료가 양념 그룹 등으로 여러 번 나와도 한 줄로 묶는다
                .groupBy(recipeIngredient.recipe.id, recipeIngredient.ingredientId, ingredient.name,
                        ingredient.basicSeasoning)
                .orderBy(recipeIngredient.recipe.id.asc(), recipeIngredient.seq.min().asc())
                .fetch();
    }

    @Override
    public List<RecipeIngredientTextDto> findIngredientTexts(Collection<Long> recipeIds) {
        return queryFactory
                .select(Projections.constructor(RecipeIngredientTextDto.class,
                        recipeIngredient.recipe.id,
                        ingredient.name,
                        recipeIngredient.displayName
                ))
                .from(recipeIngredient)
                .leftJoin(ingredient).on(ingredient.id.eq(recipeIngredient.ingredientId))
                .where(recipeIngredient.recipe.id.in(recipeIds))
                .fetch();
    }

    @Override
    public List<RecipeStepTextDto> findStepTexts(Long recipeId) {
        return queryFactory
                .select(Projections.constructor(RecipeStepTextDto.class,
                        recipeStep.recipe.id,
                        recipeStep.description
                ))
                .from(recipeStep)
                .where(recipeIdEq(recipeStep.recipe.id, recipeId))
                .orderBy(recipeStep.recipe.id.asc(), recipeStep.stepNo.asc())
                .fetch();
    }

    @Override
    public List<RecipeStepImageDto> findLastStepImages(Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return List.of();
        }
        QRecipeStep lastStep = new QRecipeStep("lastStep");
        return queryFactory
                .select(Projections.constructor(RecipeStepImageDto.class,
                        recipeStep.recipe.id,
                        recipeStep.imageUrl
                ))
                .from(recipeStep)
                .where(
                        recipeStep.recipe.id.in(recipeIds),
                        recipeStep.stepNo.eq(JPAExpressions
                                .select(lastStep.stepNo.max())
                                .from(lastStep)
                                .where(lastStep.recipe.id.eq(recipeStep.recipe.id))),
                        recipeStep.imageUrl.isNotNull(),
                        recipeStep.imageUrl.trim().isNotEmpty()
                )
                .fetch();
    }

    // 검색어가 이름 앞쪽에 있을수록 먼저, 같으면 가나다순. 중간 일치라 인덱스는 타지 않지만 레시피 수가 적어 전체를 읽어도 된다
    @Override
    public List<String> findNamesContaining(String keyword, int limit) {
        return queryFactory
                .select(recipe.name)
                .from(recipe)
                .where(nameContains(keyword))
                .groupBy(recipe.name)
                .orderBy(recipe.name.locate(keyword).asc(), recipe.name.asc())
                .limit(limit)
                .fetch();
    }

    private List<String> findDistinctValues(StringPath column) {
        return queryFactory
                .select(column)
                .distinct()
                .from(recipe)
                .where(column.isNotNull(), column.trim().isNotEmpty())
                .orderBy(column.asc())
                .fetch();
    }

    private BooleanExpression nameContains(String keyword) {
        if (keyword == null) {
            return null;
        }
        return recipe.name.likeIgnoreCase("%" + escapeLike(keyword) + "%", LIKE_ESCAPE);
    }

    private BooleanExpression cuisineTypeEq(String cuisineType) {
        return cuisineType == null ? null : recipe.cuisineType.eq(cuisineType);
    }

    private BooleanExpression categoryEq(String category) {
        return category == null ? null : recipe.category.eq(category);
    }

    // 조리 시간이 비어 있는 레시피는 조건을 걸면 제외된다.
    private BooleanExpression cookTimeLoe(Integer maxCookTimeMin) {
        return maxCookTimeMin == null ? null : recipe.cookTimeMin.loe(maxCookTimeMin);
    }

    private BooleanExpression difficultyLoe(Integer maxDifficulty) {
        return maxDifficulty == null ? null : recipe.difficulty.loe(maxDifficulty);
    }

    private BooleanExpression recipeIdEq(NumberPath<Long> path, Long recipeId) {
        return recipeId == null ? null : path.eq(recipeId);
    }

    // 같은 값끼리는 최신 레시피(id 내림차순)가 먼저 오도록 2차 정렬을 둔다.
    private OrderSpecifier<?>[] orderSpecifiers(RecipeSort sort) {
        return switch (sort) {
            // MySQL에는 관련도 점수가 없어 최신순으로 둔다 (ES 장애 대체, engine=mysql)
            case LATEST, RELEVANCE -> new OrderSpecifier<?>[]{recipe.id.desc()};
            case NAME_ASC -> new OrderSpecifier<?>[]{recipe.name.asc(), recipe.id.desc()};
            case DIFFICULTY_ASC -> new OrderSpecifier<?>[]{recipe.difficulty.asc(), recipe.id.desc()};
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

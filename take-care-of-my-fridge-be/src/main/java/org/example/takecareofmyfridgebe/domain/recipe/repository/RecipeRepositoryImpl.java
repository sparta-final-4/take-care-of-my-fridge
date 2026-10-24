package org.example.takecareofmyfridgebe.domain.recipe.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipe.recipe;

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

    // 같은 값끼리는 최신 레시피(id 내림차순)가 먼저 오도록 2차 정렬을 둔다.
    private OrderSpecifier<?>[] orderSpecifiers(RecipeSort sort) {
        return switch (sort) {
            case LATEST -> new OrderSpecifier<?>[]{recipe.id.desc()};
            case NAME_ASC -> new OrderSpecifier<?>[]{recipe.name.asc(), recipe.id.desc()};
            case DIFFICULTY_ASC -> new OrderSpecifier<?>[]{recipe.difficulty.asc(), recipe.id.desc()};
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

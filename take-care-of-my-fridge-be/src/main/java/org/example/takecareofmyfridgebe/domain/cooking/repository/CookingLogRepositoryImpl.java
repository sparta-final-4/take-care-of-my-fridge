package org.example.takecareofmyfridgebe.domain.cooking.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeDto;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.example.takecareofmyfridgebe.domain.cooking.entity.QCookingLog.cookingLog;
import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipe.recipe;
import static org.example.takecareofmyfridgebe.domain.review.entity.QRecipeReview.recipeReview;

@Repository
@RequiredArgsConstructor
public class CookingLogRepositoryImpl implements CookingLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CookingLog> findLogsByCondition(
            Long memberId,
            Long recipeId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }

        List<CookingLog> content = queryFactory
                .selectFrom(cookingLog)
                .where(
                        cookingLog.memberId.eq(memberId),
                        eqRecipeId(recipeId),
                        createdFrom(from),
                        createdUntil(to)
                )
                .orderBy(cookingLog.createdAt.desc(), cookingLog.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(cookingLog.count())
                .from(cookingLog)
                .where(
                        cookingLog.memberId.eq(memberId),
                        eqRecipeId(recipeId),
                        createdFrom(from),
                        createdUntil(to)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<MyRecipeResponse> findMyRecipes(
            Long memberId,
            MyRecipeReviewStatus reviewStatus,
            MyRecipeSortType sort,
            Pageable pageable
    ) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }

        List<MyRecipeResponse> content = queryFactory
                .select(Projections.constructor(
                        MyRecipeResponse.class,
                        cookingLog.recipeId,
                        recipe.name,
                        recipe.imageUrl,
                        cookingLog.count(),
                        cookingLog.createdAt.max(),
                        new CaseBuilder()
                                .when(cookingLog.chatSessionId.count().gt(0L))
                                .then(true)
                                .otherwise(false),
                        recipeReview.id,
                        recipeReview.rating,
                        recipeReview.content,
                        recipeReview.updatedAt
                ))
                .from(cookingLog)
                .leftJoin(recipe).on(recipe.id.eq(cookingLog.recipeId))
                .leftJoin(recipeReview).on(
                        recipeReview.member.id.eq(cookingLog.memberId)
                                .and(recipeReview.recipeId.eq(cookingLog.recipeId))
                )
                .where(
                        cookingLog.memberId.eq(memberId),
                        reviewStatusCondition(reviewStatus)
                )
                .groupBy(
                        cookingLog.recipeId,
                        recipe.name,
                        recipe.imageUrl,
                        recipeReview.id,
                        recipeReview.rating,
                        recipeReview.content,
                        recipeReview.updatedAt
                )
                .orderBy(sortOrders(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(cookingLog.recipeId.countDistinct())
                .from(cookingLog)
                .leftJoin(recipeReview).on(
                        recipeReview.member.id.eq(cookingLog.memberId)
                                .and(recipeReview.recipeId.eq(cookingLog.recipeId))
                )
                .where(
                        cookingLog.memberId.eq(memberId),
                        reviewStatusCondition(reviewStatus)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public MyRecipeSummaryCount getMyRecipesSummaryCount(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }

        MyRecipeSummaryCount summary = queryFactory
                .select(Projections.constructor(
                        MyRecipeSummaryCount.class,
                        cookingLog.recipeId.countDistinct(),
                        cookingLog.count(),
                        recipeReview.recipeId.countDistinct()
                ))
                .from(cookingLog)
                .leftJoin(recipeReview).on(
                        recipeReview.member.id.eq(cookingLog.memberId)
                                .and(recipeReview.recipeId.eq(cookingLog.recipeId))
                )
                .where(cookingLog.memberId.eq(memberId))
                .fetchOne();

        return summary != null ? summary : MyRecipeSummaryCount.empty();
    }

    @Override
    public Optional<MostCookedRecipeDto> findMostCookedRecipe(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }

        MostCookedRecipeDto result = queryFactory
                .select(Projections.constructor(
                        MostCookedRecipeDto.class,
                        cookingLog.recipeId,
                        cookingLog.count()
                ))
                .from(cookingLog)
                .where(cookingLog.memberId.eq(memberId))
                .groupBy(cookingLog.recipeId)
                .orderBy(
                        cookingLog.count().desc(),
                        cookingLog.createdAt.max().desc(),
                        cookingLog.recipeId.desc()
                )
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public CookingStatsResponse getCookingStats(Long recipeId) {
        if (recipeId == null) {
            throw new IllegalArgumentException("레시피 ID는 필수입니다.");
        }

        NumberExpression<Long> successCase = new CaseBuilder()
                .when(cookingLog.result.eq(CookingResult.SUCCESS)).then(1L)
                .otherwise(0L);

        NumberExpression<Long> failedCase = new CaseBuilder()
                .when(cookingLog.result.eq(CookingResult.FAILED)).then(1L)
                .otherwise(0L);

        CookingStatsCount count = queryFactory
                .select(Projections.constructor(
                        CookingStatsCount.class,
                        cookingLog.count(),
                        successCase.sum(),
                        failedCase.sum()
                ))
                .from(cookingLog)
                .where(cookingLog.recipeId.eq(recipeId))
                .fetchOne();

        return CookingStatsResponse.of(recipeId, count);
    }

    private BooleanExpression eqRecipeId(Long recipeId) {
        return recipeId != null ? cookingLog.recipeId.eq(recipeId) : null;
    }

    private BooleanExpression createdFrom(LocalDate from) {
        return from != null ? cookingLog.createdAt.goe(from.atStartOfDay()) : null;
    }

    private BooleanExpression createdUntil(LocalDate to) {
        return to != null ? cookingLog.createdAt.lt(to.plusDays(1).atStartOfDay()) : null;
    }

    private BooleanExpression reviewStatusCondition(MyRecipeReviewStatus reviewStatus) {
        if (reviewStatus == null || reviewStatus == MyRecipeReviewStatus.ALL) {
            return null;
        }
        if (reviewStatus == MyRecipeReviewStatus.WRITTEN) {
            return recipeReview.id.isNotNull();
        }
        if (reviewStatus == MyRecipeReviewStatus.NOT_WRITTEN) {
            return recipeReview.id.isNull();
        }
        return null;
    }

    private OrderSpecifier<?>[] sortOrders(MyRecipeSortType sort) {
        if (sort == MyRecipeSortType.MOST) {
            return new OrderSpecifier<?>[]{
                    cookingLog.count().desc(),
                    cookingLog.createdAt.max().desc(),
                    cookingLog.recipeId.desc()
            };
        }
        return new OrderSpecifier<?>[]{
                cookingLog.createdAt.max().desc(),
                cookingLog.recipeId.desc()
        };
    }

    @Override
    public List<PopularRecipeCookingCount> findPopularCookingCounts(LocalDateTime since, int size) {
        if (since == null || size <= 0) {
            return Collections.emptyList();
        }

        return queryFactory
                .select(Projections.constructor(
                        PopularRecipeCookingCount.class,
                        cookingLog.recipeId,
                        cookingLog.memberId.countDistinct()
                ))
                .from(cookingLog)
                .where(cookingLog.createdAt.goe(since))
                .groupBy(cookingLog.recipeId)
                .orderBy(
                        cookingLog.memberId.countDistinct().desc(),
                        cookingLog.count().desc(),
                        cookingLog.createdAt.max().desc(),
                        cookingLog.recipeId.asc()
                )
                .limit(size)
                .fetch();
    }
}

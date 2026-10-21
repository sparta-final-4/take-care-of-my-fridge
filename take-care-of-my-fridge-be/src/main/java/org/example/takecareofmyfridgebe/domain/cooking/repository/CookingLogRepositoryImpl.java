package org.example.takecareofmyfridgebe.domain.cooking.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

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
    public MyRecipeSummaryResponse getMyRecipesSummary(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }

        MyRecipeSummaryResponse summary = queryFactory
                .select(Projections.constructor(
                        MyRecipeSummaryResponse.class,
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

        return summary != null ? summary : MyRecipeSummaryResponse.empty();
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
}

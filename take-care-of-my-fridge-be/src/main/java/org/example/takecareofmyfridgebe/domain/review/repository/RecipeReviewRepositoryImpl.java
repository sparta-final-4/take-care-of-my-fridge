package org.example.takecareofmyfridgebe.domain.review.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSortType;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.member.entity.QMember.member;
import static org.example.takecareofmyfridgebe.domain.review.entity.QRecipeReview.recipeReview;

@Repository
@RequiredArgsConstructor
public class RecipeReviewRepositoryImpl implements RecipeReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<RecipeReview> findReviewsByCondition(ReviewSearchRequest request, Long currentMemberId) {
        int page = request.page() - 1;
        int size = request.size();
        PageRequest pageRequest = PageRequest.of(page, size);

        BooleanExpression predicate = buildPredicate(request, currentMemberId);

        List<RecipeReview> content = queryFactory
                .selectFrom(recipeReview)
                .join(recipeReview.member, member).fetchJoin()
                .where(predicate)
                .orderBy(orderSpecifiers(request.sort()))
                .offset((long) page * size)
                .limit(size)
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(recipeReview.count())
                .from(recipeReview)
                .where(predicate);

        Long total = countQuery.fetchOne();
        long totalCount = total != null ? total : 0L;

        return new PageImpl<>(content, pageRequest, totalCount);
    }

    private BooleanExpression buildPredicate(ReviewSearchRequest request, Long currentMemberId) {
        BooleanExpression predicate = null;

        if (request.recipeId() != null) {
            predicate = recipeReview.recipeId.eq(request.recipeId());
        }

        if (Boolean.TRUE.equals(request.mine()) && currentMemberId != null) {
            BooleanExpression mineExpression = recipeReview.member.id.eq(currentMemberId);
            predicate = predicate != null ? predicate.and(mineExpression) : mineExpression;
        }

        return predicate;
    }

    private OrderSpecifier<?>[] orderSpecifiers(ReviewSortType sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (sort == ReviewSortType.RATING) {
            orders.add(recipeReview.rating.desc());
            orders.add(recipeReview.id.desc());
        } else {
            // LATEST (기본)
            orders.add(recipeReview.id.desc());
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}

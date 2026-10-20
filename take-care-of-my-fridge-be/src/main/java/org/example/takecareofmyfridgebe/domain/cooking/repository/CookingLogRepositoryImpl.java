package org.example.takecareofmyfridgebe.domain.cooking.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.cooking.entity.QCookingLog.cookingLog;

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

    private BooleanExpression eqRecipeId(Long recipeId) {
        return recipeId != null ? cookingLog.recipeId.eq(recipeId) : null;
    }

    private BooleanExpression createdFrom(LocalDate from) {
        return from != null ? cookingLog.createdAt.goe(from.atStartOfDay()) : null;
    }

    private BooleanExpression createdUntil(LocalDate to) {
        return to != null ? cookingLog.createdAt.lt(to.plusDays(1).atStartOfDay()) : null;
    }
}

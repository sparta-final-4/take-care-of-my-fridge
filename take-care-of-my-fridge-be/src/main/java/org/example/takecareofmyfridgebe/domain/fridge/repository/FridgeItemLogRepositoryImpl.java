package org.example.takecareofmyfridgebe.domain.fridge.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.fridge.entity.QFridgeItemLog.fridgeItemLog;
import static org.example.takecareofmyfridgebe.domain.ingredient.entity.QIngredient.ingredient;

@Repository
@RequiredArgsConstructor
public class FridgeItemLogRepositoryImpl implements FridgeItemLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 회원의 냉장고 재료 기록을 동적 조건(처리 방식, 기간)으로 최신순 페이지 조회한다.
     * 응답 DTO 조립 시 Ingredient 접근으로 인한 N+1 문제를 방지하기 위해 fetchJoin을 사용하고,
     * 전체 개수는 fetchJoin 없이 별도 count 쿼리로 구한다(필요할 때만 실행).
     */
    @Override
    public Page<FridgeItemLog> findLogsByCondition(
            Long memberId,
            FridgeItemAction action,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        List<FridgeItemLog> content = queryFactory
                .selectFrom(fridgeItemLog)
                .join(fridgeItemLog.ingredient, ingredient).fetchJoin()
                .where(
                        fridgeItemLog.memberId.eq(memberId),
                        eqAction(action),
                        createdFrom(from),
                        createdUntil(to)
                )
                .orderBy(fridgeItemLog.createdAt.desc(), fridgeItemLog.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(fridgeItemLog.count())
                .from(fridgeItemLog)
                .where(
                        fridgeItemLog.memberId.eq(memberId),
                        eqAction(action),
                        createdFrom(from),
                        createdUntil(to)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 처리 방식(CONSUMED, DISCARDED) 미지정 시 null을 반환해 전체 조회
    private BooleanExpression eqAction(FridgeItemAction action) {
        return action != null ? fridgeItemLog.action.eq(action) : null;
    }

    // 시작일 00:00 이후 기록 (미지정 시 제한 없음)
    private BooleanExpression createdFrom(LocalDate from) {
        return from != null ? fridgeItemLog.createdAt.goe(from.atStartOfDay()) : null;
    }

    // 종료일 하루 전체를 포함하도록 종료일 다음 날 00:00 이전 기록 (미지정 시 제한 없음)
    private BooleanExpression createdUntil(LocalDate to) {
        return to != null ? fridgeItemLog.createdAt.lt(to.plusDays(1).atStartOfDay()) : null;
    }
}

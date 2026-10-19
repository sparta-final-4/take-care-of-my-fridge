package org.example.takecareofmyfridgebe.domain.fridge.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.fridge.entity.QFridge.fridge;
import static org.example.takecareofmyfridgebe.domain.ingredient.entity.QIngredient.ingredient;

@Repository
@RequiredArgsConstructor
public class FridgeRepositoryImpl implements FridgeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 회원의 냉장고 재료를 동적 조건(보관방식, 임박 여부)과 정렬 기준으로 조회한다.
     * 응답 DTO 조립 시 Ingredient 접근으로 인한 N+1 문제를 방지하기 위해 fetchJoin을 사용한다.
     */
    @Override
    public List<Fridge> findItemsByCondition(
            Long memberId,
            StorageType storage,
            FridgeSortType sort,
            boolean imminentOnly,
            LocalDate today
    ) {
        return queryFactory
                .selectFrom(fridge)
                .join(fridge.ingredient, ingredient).fetchJoin()
                .where(
                        fridge.memberId.eq(memberId),
                        eqStorage(storage),
                        imminentCondition(imminentOnly, today)
                )
                .orderBy(orderSpecifiers(sort))
                .fetch();
    }

    // 보관방식(REFRIGERATED, FROZEN, ROOM_TEMP) 미지정 시 null을 반환해 전체 조회
    private BooleanExpression eqStorage(StorageType storage) {
        return storage != null ? fridge.storage.eq(storage) : null;
    }

    // 임박 재료 필터: 만료된 재료(오늘 이전)는 제외하고, 오늘 ~ 오늘+IMMINENT_DAYS(D-3) 이내만 조회
    private BooleanExpression imminentCondition(boolean imminentOnly, LocalDate today) {
        if (!imminentOnly) {
            return null;
        }
        return fridge.expirationDate.between(today, today.plusDays(Fridge.IMMINENT_DAYS));
    }

    // 정렬 조건: 1차 정렬 기준 값이 같을 때 조회 순서가 항상 일정하도록 id를 2차 정렬로 함께 지정
    private OrderSpecifier<?>[] orderSpecifiers(FridgeSortType sort) {
        FridgeSortType resolvedSort = sort != null ? sort : FridgeSortType.EXPIRATION;
        return switch (resolvedSort) {
            case EXPIRATION -> new OrderSpecifier<?>[]{fridge.expirationDate.asc(), fridge.id.asc()};
            case CREATED -> new OrderSpecifier<?>[]{fridge.createdAt.desc(), fridge.id.desc()};
            case NAME -> new OrderSpecifier<?>[]{ingredient.name.asc(), fridge.rawName.asc(), fridge.id.asc()};
        };
    }
}

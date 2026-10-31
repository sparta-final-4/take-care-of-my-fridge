package org.example.takecareofmyfridgebe.domain.fridge.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.example.takecareofmyfridgebe.domain.fridge.entity.QFridge.fridge;
import static org.example.takecareofmyfridgebe.domain.ingredient.entity.QIngredient.ingredient;

@Repository
@RequiredArgsConstructor
public class FridgeRepositoryImpl implements FridgeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 회원의 냉장고 재료 전체를 정렬 기준으로 조회한다.
     * 탭(보관방식·밀키트·기본 양념)과 임박 구분은 프론트가 받은 전체 목록에서 거른다.
     * 응답 DTO 조립 시 Ingredient 접근으로 인한 N+1 문제를 방지하기 위해 fetchJoin을 사용한다.
     */
    @Override
    public List<Fridge> findItemsByCondition(
            Long memberId,
            FridgeSortType sort
    ) {
        return queryFactory
                .selectFrom(fridge)
                .join(fridge.ingredient, ingredient).fetchJoin()
                .where(fridge.memberId.eq(memberId))
                .orderBy(orderSpecifiers(sort))
                .fetch();
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

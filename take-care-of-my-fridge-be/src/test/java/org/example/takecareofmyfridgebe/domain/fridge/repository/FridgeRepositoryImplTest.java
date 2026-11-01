package org.example.takecareofmyfridgebe.domain.fridge.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.example.takecareofmyfridgebe.domain.fridge.entity.QFridge.fridge;
import static org.example.takecareofmyfridgebe.domain.ingredient.entity.QIngredient.ingredient;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FridgeRepositoryImpl의 QueryDSL 쿼리 조립 단위 테스트.
 * JPAQueryFactory를 모킹하여 join/fetchJoin 호출 여부, 회원 where 조건,
 * 그리고 정렬 기준(EXPIRATION, CREATED, NAME + 2차 정렬 id)이 정확하게 생성되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FridgeRepositoryImplTest {

    @Mock
    private JPAQueryFactory queryFactory;

    @Mock
    private JPAQuery<Fridge> jpaQuery;

    private FridgeRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        // QueryDSL 체이닝 메서드(selectFrom -> join -> fetchJoin -> where -> orderBy -> fetch) 스터빙
        repository = new FridgeRepositoryImpl(queryFactory);
        when(queryFactory.selectFrom(fridge)).thenReturn(jpaQuery);
        when(jpaQuery.join(fridge.ingredient, ingredient)).thenReturn(jpaQuery);
        when(jpaQuery.fetchJoin()).thenReturn(jpaQuery);
        // memberId가 null이면 where 조건 생성 단계에서 예외가 나 이후 체인이 호출되지 않으므로 lenient로 스터빙
        lenient().when(jpaQuery.where(any(Predicate.class))).thenReturn(jpaQuery);
        lenient().when(jpaQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(jpaQuery);
        lenient().when(jpaQuery.fetch()).thenReturn(List.of());
    }

    /**
     * 회원 조건은 필수 조건이므로 memberId가 null이면
     * 전체 회원의 재료를 조회하지 않고 예외가 발생하는지 검증한다.
     */
    @Test
    void findItemsByConditionThrowsWhenMemberIdIsNull() {
        // When & Then: 예외 발생 및 쿼리 미실행 확인
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findItemsByCondition(null, FridgeSortType.EXPIRATION)
        );
        verify(jpaQuery, never()).fetch();
    }

    /**
     * 기본 정렬(EXPIRATION)일 때:
     * 1) Ingredient fetchJoin이 호출되는지
     * 2) where 절에 memberId 조건만 들어가는지
     * 3) orderBy 절이 expirationDate ASC, id ASC 순서로 들어가는지 검증한다.
     */
    @Test
    void findItemsByConditionWithDefaultExpirationSortFiltersByMemberOnly() {
        // When
        repository.findItemsByCondition(7L, FridgeSortType.EXPIRATION);

        // Then: N+1 방지용 fetchJoin 확인
        verify(jpaQuery).join(fridge.ingredient, ingredient);
        verify(jpaQuery).fetchJoin();

        // Then: where 조건 캡처 및 확인 (memberId만 적용)
        ArgumentCaptor<Predicate> whereCaptor = ArgumentCaptor.forClass(Predicate.class);
        verify(jpaQuery).where(whereCaptor.capture());
        assertEquals(fridge.memberId.eq(7L), whereCaptor.getValue());

        // Then: 정렬 조건 캡처 및 확인 (소비기한 오름차순 + id 오름차순)
        ArgumentCaptor<OrderSpecifier<?>[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(jpaQuery).orderBy(orderCaptor.capture());
        assertArrayEquals(
                new OrderSpecifier<?>[]{fridge.expirationDate.asc(), fridge.id.asc()},
                orderCaptor.getValue()
        );
    }

    /**
     * 최신 등록순(CREATED) 정렬 선택 시
     * orderBy 절이 createdAt DESC, id DESC 순서로 들어가는지 검증한다.
     */
    @Test
    void findItemsByConditionWithCreatedSortUsesCreatedAtDescThenIdDesc() {
        // When
        repository.findItemsByCondition(7L, FridgeSortType.CREATED);

        // Then: 정렬 조건 확인 (등록일 내림차순 + id 내림차순)
        ArgumentCaptor<OrderSpecifier<?>[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(jpaQuery).orderBy(orderCaptor.capture());
        assertArrayEquals(
                new OrderSpecifier<?>[]{fridge.createdAt.desc(), fridge.id.desc()},
                orderCaptor.getValue()
        );
    }

    /**
     * 재료명 가나다순(NAME) 정렬 선택 시
     * orderBy 절이 ingredient.name ASC, rawName ASC, id ASC 순서로 들어가는지 검증한다.
     */
    @Test
    void findItemsByConditionWithNameSortUsesIngredientNameThenRawNameThenId() {
        // When
        repository.findItemsByCondition(7L, FridgeSortType.NAME);

        // Then: 정렬 조건 확인 (표준 재료명 오름차순 + 표시명 오름차순 + id 오름차순)
        ArgumentCaptor<OrderSpecifier<?>[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(jpaQuery).orderBy(orderCaptor.capture());
        assertArrayEquals(
                new OrderSpecifier<?>[]{ingredient.name.asc(), fridge.rawName.asc(), fridge.id.asc()},
                orderCaptor.getValue()
        );
    }
}

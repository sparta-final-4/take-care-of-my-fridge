package org.example.takecareofmyfridgebe.domain.fridge.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.fridge.entity.QFridge.fridge;
import static org.example.takecareofmyfridgebe.domain.ingredient.entity.QIngredient.ingredient;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FridgeRepositoryImpl의 QueryDSL 쿼리 조립 단위 테스트.
 * JPAQueryFactory를 모킹하여 join/fetchJoin 호출 여부, 동적 where 조건(storage, imminentOnly),
 * 그리고 정렬 기준(EXPIRATION, CREATED, NAME + 2차 정렬 id ASC)이 정확하게 생성되는지 검증한다.
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
        when(jpaQuery.where(any(Predicate[].class))).thenReturn(jpaQuery);
        when(jpaQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(jpaQuery);
        when(jpaQuery.fetch()).thenReturn(List.of());
    }

    /**
     * 보관방식 미지정(null), 임박 필터 해제(false), 기본 정렬(EXPIRATION)일 때:
     * 1) Ingredient fetchJoin이 호출되는지
     * 2) where 절에 memberId 조건만 들어가고 storage·imminent 조건은 null(생략)인지
     * 3) orderBy 절이 expirationDate ASC, id ASC 순서로 들어가는지 검증한다.
     */
    @Test
    void findItemsByConditionWithDefaultExpirationSortAndAllItems() {
        // Given
        LocalDate today = LocalDate.of(2026, 10, 19);

        // When
        repository.findItemsByCondition(7L, null, FridgeSortType.EXPIRATION, false, today);

        // Then: N+1 방지용 fetchJoin 확인
        verify(jpaQuery).join(fridge.ingredient, ingredient);
        verify(jpaQuery).fetchJoin();

        // Then: where 조건 캡처 및 확인 (memberId만 적용, 나머지 필터는 null)
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(jpaQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertEquals(3, predicates.length);
        assertEquals(fridge.memberId.eq(7L), predicates[0]);
        assertNull(predicates[1]);
        assertNull(predicates[2]);

        // Then: 정렬 조건 캡처 및 확인 (소비기한 오름차순 + id 오름차순)
        ArgumentCaptor<OrderSpecifier<?>[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(jpaQuery).orderBy(orderCaptor.capture());
        assertArrayEquals(
                new OrderSpecifier<?>[]{fridge.expirationDate.asc(), fridge.id.asc()},
                orderCaptor.getValue()
        );
    }

    /**
     * 보관방식(REFRIGERATED), 임박 필터(imminentOnly = true), 최근 등록 순(CREATED)일 때:
     * 1) where 절에 storage = REFRIGERATED 조건과 expirationDate BETWEEN today AND today+3 조건이 들어가는지
     * 2) orderBy 절이 createdAt DESC, id ASC 순서로 들어가는지 검증한다.
     */
    @Test
    void findItemsByConditionWithCreatedSortAndImminentOnly() {
        // Given
        LocalDate today = LocalDate.of(2026, 10, 19);

        // When
        repository.findItemsByCondition(7L, StorageType.REFRIGERATED, FridgeSortType.CREATED, true, today);

        // Then: where 조건에 보관방식과 오늘~오늘+IMMINENT_DAYS 범위 조건이 모두 포함되는지 확인
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(jpaQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertEquals(fridge.memberId.eq(7L), predicates[0]);
        assertEquals(fridge.storage.eq(StorageType.REFRIGERATED), predicates[1]);
        assertEquals(
                fridge.expirationDate.between(today, today.plusDays(Fridge.IMMINENT_DAYS)),
                predicates[2]
        );

        // Then: 정렬 조건 확인 (등록일 내림차순 + id 오름차순)
        ArgumentCaptor<OrderSpecifier<?>[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(jpaQuery).orderBy(orderCaptor.capture());
        assertArrayEquals(
                new OrderSpecifier<?>[]{fridge.createdAt.desc(), fridge.id.asc()},
                orderCaptor.getValue()
        );
    }

    /**
     * 재료명 가나다순(NAME) 정렬 선택 시
     * orderBy 절이 rawName ASC, id ASC 순서로 들어가는지 검증한다.
     */
    @Test
    void findItemsByConditionWithNameSortUsesRawNameAscAndIdAsc() {
        // Given
        LocalDate today = LocalDate.of(2026, 10, 19);

        // When
        repository.findItemsByCondition(7L, StorageType.FROZEN, FridgeSortType.NAME, false, today);

        // Then: 정렬 조건 확인 (표시명 오름차순 + id 오름차순)
        ArgumentCaptor<OrderSpecifier<?>[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(jpaQuery).orderBy(orderCaptor.capture());
        assertArrayEquals(
                new OrderSpecifier<?>[]{fridge.rawName.asc(), fridge.id.asc()},
                orderCaptor.getValue()
        );
    }
}

package org.example.takecareofmyfridgebe.domain.cooking.repository;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.example.takecareofmyfridgebe.domain.cooking.entity.QCookingLog.cookingLog;
import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipe.recipe;
import static org.example.takecareofmyfridgebe.domain.review.entity.QRecipeReview.recipeReview;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CookingLogRepositoryImpl의 QueryDSL 쿼리 조립 단위 테스트.
 * JPAQueryFactory를 모킹하여 join 호출 여부, 동적 where 조건(reviewStatus),
 * group by, 정렬 기준(RECENT vs MOST), summary 단일 쿼리 조립을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CookingLogRepositoryImplTest {

    @Mock
    private JPAQueryFactory queryFactory;

    @Mock
    private JPAQuery<CookingLog> cookingLogQuery;

    @Mock
    private JPAQuery<MyRecipeResponse> myRecipeQuery;

    @Mock
    private JPAQuery<MyRecipeSummaryResponse> summaryQuery;

    @Mock
    private JPAQuery<CookingStatsCount> statsCountQuery;

    @Mock
    private JPAQuery<Long> longQuery;

    private CookingLogRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CookingLogRepositoryImpl(queryFactory);
    }

    /**
     * findLogsByCondition 호출 시 memberId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    void findLogsByConditionThrowsWhenMemberIdIsNull() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findLogsByCondition(null, null, null, null, PageRequest.of(0, 10))
        );
        verify(queryFactory, never()).selectFrom(any());
    }

    /**
     * findMyRecipes 호출 시 memberId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    void findMyRecipesThrowsWhenMemberIdIsNull() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findMyRecipes(null, MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, PageRequest.of(0, 10))
        );
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * getMyRecipesSummary 호출 시 memberId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    void getMyRecipesSummaryThrowsWhenMemberIdIsNull() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.getMyRecipesSummary(null)
        );
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * 기본 조회(reviewStatus=ALL, sort=RECENT)일 때:
     * 1) Recipe 및 RecipeReview left join이 정확히 호출되는지
     * 2) where 절에 memberId 조건만 들어가고 reviewStatus 필터는 null(생략)인지
     * 3) orderBy 절이 max(createdAt) DESC, recipeId DESC 순서로 들어가는지 검증한다.
     */
    @Test
    void findMyRecipesWithDefaultCondition() {
        // Given
        Long memberId = 7L;
        PageRequest pageRequest = PageRequest.of(0, 10);

        when(queryFactory.select(any(Expression.class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.from(cookingLog)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.leftJoin(recipe)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.on(recipe.id.eq(cookingLog.recipeId))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.leftJoin(recipeReview)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.where(any(Predicate[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.groupBy(any(Expression[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.offset(0L)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.limit(10L)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.fetch()).thenReturn(List.of());

        // countQuery mocking (lenient: PageableExecutionUtils가 첫 페이지 빈 리스트일 때 countQuery 호출을 생략할 수 있음)
        lenient().when(queryFactory.select(cookingLog.recipeId.countDistinct())).thenReturn(longQuery);
        lenient().when(longQuery.from(cookingLog)).thenReturn(longQuery);
        lenient().when(longQuery.leftJoin(recipeReview)).thenReturn(longQuery);
        lenient().when(longQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(longQuery);
        lenient().when(longQuery.where(any(Predicate[].class))).thenReturn(longQuery);
        lenient().when(longQuery.fetchOne()).thenReturn(0L);

        // When
        Page<MyRecipeResponse> result = repository.findMyRecipes(
                memberId, MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, pageRequest);

        // Then: Recipe 및 Review left join 호출 확인
        verify(myRecipeQuery).leftJoin(recipe);
        verify(myRecipeQuery).leftJoin(recipeReview);

        // Then: where 조건 캡처 및 확인 (reviewStatus=ALL 이므로 두 번째 인자는 null)
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(myRecipeQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertEquals(cookingLog.memberId.eq(memberId), predicates[0]);
        assertNull(predicates[1]);

        // Then: orderBy 정렬 확인 (RECENT: max(createdAt) desc, recipeId desc)
        ArgumentCaptor<OrderSpecifier[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(myRecipeQuery).orderBy(orderCaptor.capture());
        OrderSpecifier[] orderSpecifiers = orderCaptor.getValue();
        assertEquals(cookingLog.createdAt.max().desc(), orderSpecifiers[0]);
        assertEquals(cookingLog.recipeId.desc(), orderSpecifiers[1]);

        assertEquals(0, result.getTotalElements());
    }

    /**
     * reviewStatus=WRITTEN, sort=MOST 조회일 때:
     * 1) where 절에 recipeReview.id IS NOT NULL 조건이 추가되는지
     * 2) orderBy 절이 count() DESC, max(createdAt) DESC, recipeId DESC 순서로 들어가는지 검증한다.
     */
    @Test
    void findMyRecipesWithWrittenFilterAndMostSort() {
        // Given
        Long memberId = 7L;
        PageRequest pageRequest = PageRequest.of(1, 5);

        when(queryFactory.select(any(Expression.class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.from(cookingLog)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.leftJoin(recipe)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.on(recipe.id.eq(cookingLog.recipeId))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.leftJoin(recipeReview)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.where(any(Predicate[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.groupBy(any(Expression[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.offset(5L)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.limit(5L)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.fetch()).thenReturn(List.of());

        // countQuery mocking
        lenient().when(queryFactory.select(cookingLog.recipeId.countDistinct())).thenReturn(longQuery);
        lenient().when(longQuery.from(cookingLog)).thenReturn(longQuery);
        lenient().when(longQuery.leftJoin(recipeReview)).thenReturn(longQuery);
        lenient().when(longQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(longQuery);
        lenient().when(longQuery.where(any(Predicate[].class))).thenReturn(longQuery);
        lenient().when(longQuery.fetchOne()).thenReturn(0L);

        // When
        repository.findMyRecipes(memberId, MyRecipeReviewStatus.WRITTEN, MyRecipeSortType.MOST, pageRequest);

        // Then: where 조건 캡처 및 확인 (reviewStatus=WRITTEN -> recipeReview.id.isNotNull())
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(myRecipeQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertEquals(cookingLog.memberId.eq(memberId), predicates[0]);
        assertEquals(recipeReview.id.isNotNull(), predicates[1]);

        // Then: orderBy 정렬 확인 (MOST: count() desc, max(createdAt) desc, recipeId desc)
        ArgumentCaptor<OrderSpecifier[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(myRecipeQuery).orderBy(orderCaptor.capture());
        OrderSpecifier[] orderSpecifiers = orderCaptor.getValue();
        assertEquals(cookingLog.count().desc(), orderSpecifiers[0]);
        assertEquals(cookingLog.createdAt.max().desc(), orderSpecifiers[1]);
        assertEquals(cookingLog.recipeId.desc(), orderSpecifiers[2]);
    }

    /**
     * reviewStatus=NOT_WRITTEN 조회 시 where 절에 recipeReview.id IS NULL 조건이 적용되는지 검증한다.
     */
    @Test
    void findMyRecipesWithNotWrittenFilter() {
        // Given
        Long memberId = 7L;
        PageRequest pageRequest = PageRequest.of(0, 10);

        when(queryFactory.select(any(Expression.class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.from(cookingLog)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.leftJoin(recipe)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.on(recipe.id.eq(cookingLog.recipeId))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.leftJoin(recipeReview)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.where(any(Predicate[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.groupBy(any(Expression[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(myRecipeQuery);
        when(myRecipeQuery.offset(0L)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.limit(10L)).thenReturn(myRecipeQuery);
        when(myRecipeQuery.fetch()).thenReturn(List.of());

        lenient().when(queryFactory.select(cookingLog.recipeId.countDistinct())).thenReturn(longQuery);
        lenient().when(longQuery.from(cookingLog)).thenReturn(longQuery);
        lenient().when(longQuery.leftJoin(recipeReview)).thenReturn(longQuery);
        lenient().when(longQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(longQuery);
        lenient().when(longQuery.where(any(Predicate[].class))).thenReturn(longQuery);
        lenient().when(longQuery.fetchOne()).thenReturn(0L);

        // When
        repository.findMyRecipes(memberId, MyRecipeReviewStatus.NOT_WRITTEN, MyRecipeSortType.RECENT, pageRequest);

        // Then: where 조건 (recipeReview.id.isNull())
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(myRecipeQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertEquals(recipeReview.id.isNull(), predicates[1]);
    }

    /**
     * getMyRecipesSummary 단일 쿼리로 recipeCount, totalCookCount, writtenCount를 조회하고
     * notWrittenCount(recipeCount - writtenCount)가 자동 산출되는지 검증한다.
     */
    @Test
    void getMyRecipesSummaryBuildsSingleQueryAndCalculatesCounts() {
        // Given: 레시피 3개, 총 요리 7회, 후기 2건 작성 -> 미작성 1건
        Long memberId = 7L;
        MyRecipeSummaryResponse expectedSummary = new MyRecipeSummaryResponse(3L, 7L, 2L);

        when(queryFactory.select(any(Expression.class))).thenReturn(summaryQuery);
        when(summaryQuery.from(cookingLog)).thenReturn(summaryQuery);
        when(summaryQuery.leftJoin(recipeReview)).thenReturn(summaryQuery);
        when(summaryQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(summaryQuery);
        when(summaryQuery.where(cookingLog.memberId.eq(memberId))).thenReturn(summaryQuery);
        when(summaryQuery.fetchOne()).thenReturn(expectedSummary);

        // When
        MyRecipeSummaryResponse summary = repository.getMyRecipesSummary(memberId);

        // Then: 조인 및 조건 호출 확인과 자동 계산된 notWrittenCount 확인
        assertEquals(3L, summary.recipeCount());
        assertEquals(7L, summary.totalCookCount());
        assertEquals(2L, summary.writtenCount());
        assertEquals(1L, summary.notWrittenCount()); // 3 - 2 = 1

        verify(summaryQuery).leftJoin(recipeReview);
        verify(summaryQuery).where(cookingLog.memberId.eq(memberId));
    }

    /**
     * getCookingStats 호출 시 recipeId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    void getCookingStatsThrowsWhenRecipeIdIsNull() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.getCookingStats(null)
        );
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * getCookingStats 호출 시 cookingLog를 대상으로 단일 집계 쿼리를 조립하고
     * 결과 DTO에 recipeId와 집계 카운트를 결합하여 반환하는지 검증한다.
     */
    @Test
    void getCookingStatsBuildsSingleQueryAndReturnsResponse() {
        // Given
        Long recipeId = 12L;
        CookingStatsCount countResult = new CookingStatsCount(10L, 7L, 3L);

        when(queryFactory.select(any(Expression.class))).thenReturn(statsCountQuery);
        when(statsCountQuery.from(cookingLog)).thenReturn(statsCountQuery);
        when(statsCountQuery.where(cookingLog.recipeId.eq(recipeId))).thenReturn(statsCountQuery);
        when(statsCountQuery.fetchOne()).thenReturn(countResult);

        // When
        CookingStatsResponse response = repository.getCookingStats(recipeId);

        // Then
        assertEquals(recipeId, response.recipeId());
        assertEquals(10L, response.totalCount());
        assertEquals(7L, response.successCount());
        assertEquals(3L, response.failedCount());

        verify(statsCountQuery).from(cookingLog);
        verify(statsCountQuery).where(cookingLog.recipeId.eq(recipeId));
    }

    /**
     * getCookingStats 호출 시 쿼리 결과가 null이면 모든 카운트가 0인 응답을 반환하는지 검증한다.
     */
    @Test
    void getCookingStatsReturnsZeroWhenFetchOneIsNull() {
        // Given
        Long recipeId = 12L;

        when(queryFactory.select(any(Expression.class))).thenReturn(statsCountQuery);
        when(statsCountQuery.from(cookingLog)).thenReturn(statsCountQuery);
        when(statsCountQuery.where(cookingLog.recipeId.eq(recipeId))).thenReturn(statsCountQuery);
        when(statsCountQuery.fetchOne()).thenReturn(null);

        // When
        CookingStatsResponse response = repository.getCookingStats(recipeId);

        // Then
        assertEquals(recipeId, response.recipeId());
        assertEquals(0L, response.totalCount());
        assertEquals(0L, response.successCount());
        assertEquals(0L, response.failedCount());
    }
}

package org.example.takecareofmyfridgebe.domain.cooking.repository;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeDto;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryCount;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.takecareofmyfridgebe.domain.cooking.entity.QCookingLog.cookingLog;
import static org.example.takecareofmyfridgebe.domain.recipe.entity.QRecipe.recipe;
import static org.example.takecareofmyfridgebe.domain.review.entity.QRecipeReview.recipeReview;
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
    private JPAQuery<MyRecipeSummaryCount> summaryQuery;

    @Mock
    private JPAQuery<MostCookedRecipeDto> mostCookedQuery;

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
    @DisplayName("요리 기록 조회에서 회원 ID가 없으면 쿼리를 실행하지 않고 예외를 던진다")
    void throwsIllegalArgument_whenLogsMemberIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> repository.findLogsByCondition(null, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(queryFactory, never()).selectFrom(any());
    }

    /**
     * findMyRecipes 호출 시 memberId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피 조회에서 회원 ID가 없으면 쿼리를 실행하지 않고 예외를 던진다")
    void throwsIllegalArgument_whenMyRecipesMemberIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> repository.findMyRecipes(null, MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * getMyRecipesSummaryCount 호출 시 memberId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피 요약 조회에서 회원 ID가 없으면 쿼리를 실행하지 않고 예외를 던진다")
    void throwsIllegalArgument_whenSummaryMemberIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> repository.getMyRecipesSummaryCount(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * findMostCookedRecipe 호출 시 memberId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    @DisplayName("가장 많이 만든 레시피 조회에서 회원 ID가 없으면 쿼리를 실행하지 않고 예외를 던진다")
    void throwsIllegalArgument_whenMostCookedMemberIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> repository.findMostCookedRecipe(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * findMyRecipes 호출 시 recipe 및 recipeReview 테이블과의 left join이 호출되고,
     * 기본 ALL 조회에서는 where 절에 reviewId 조건이 포함되지 않는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피를 전체 조건으로 조회하면 레시피·후기를 left join하고 후기 조건은 넣지 않는다")
    void joinsRecipeAndReviewWithoutReviewFilter_whenStatusIsAll() {
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
        lenient().when(longQuery.leftJoin(recipeReview)).thenReturn(longQuery);
        lenient().when(longQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(longQuery);
        lenient().when(longQuery.where(any(Predicate[].class))).thenReturn(longQuery);
        lenient().when(longQuery.fetchOne()).thenReturn(0L);

        // When
        Page<MyRecipeResponse> result = repository.findMyRecipes(
                memberId, MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, pageRequest);

        // Then: 조인 호출 확인
        verify(myRecipeQuery).leftJoin(recipe);
        verify(myRecipeQuery).leftJoin(recipeReview);

        // where 절에 memberId 조건만 포함되고 reviewStatus 조건(null)은 없음 확인
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(myRecipeQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertThat(predicates[0]).isEqualTo(cookingLog.memberId.eq(memberId));
        assertThat(predicates[1]).isNull();

        assertThat(result.getContent().size()).isEqualTo(0);
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    /**
     * reviewStatus=WRITTEN, sort=MOST 조회일 때:
     * 1) where 절에 recipeReview.id IS NOT NULL 조건이 추가되는지
     * 2) orderBy 절이 count() DESC, max(createdAt) DESC, recipeId DESC 순서로 들어가는지 검증한다.
     */
    @Test
    @DisplayName("후기 작성 레시피를 많이 만든 순으로 조회하면 후기 있음 조건과 요리 횟수순 정렬을 넣는다")
    void filtersWrittenAndSortsByCount_whenWrittenAndMost() {
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

        lenient().when(queryFactory.select(cookingLog.recipeId.countDistinct())).thenReturn(longQuery);
        lenient().when(longQuery.from(cookingLog)).thenReturn(longQuery);
        lenient().when(longQuery.leftJoin(recipeReview)).thenReturn(longQuery);
        lenient().when(longQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(longQuery);
        lenient().when(longQuery.where(any(Predicate[].class))).thenReturn(longQuery);
        lenient().when(longQuery.fetchOne()).thenReturn(0L);
        lenient().when(longQuery.leftJoin(recipeReview)).thenReturn(longQuery);
        lenient().when(longQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(longQuery);
        lenient().when(longQuery.where(any(Predicate[].class))).thenReturn(longQuery);
        lenient().when(longQuery.fetchOne()).thenReturn(0L);

        // When
        repository.findMyRecipes(memberId, MyRecipeReviewStatus.WRITTEN, MyRecipeSortType.MOST, pageRequest);

        // Then: where 조건 캡처 및 확인
        ArgumentCaptor<Predicate[]> whereCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(myRecipeQuery).where(whereCaptor.capture());
        Predicate[] predicates = whereCaptor.getValue();
        assertThat(predicates[0]).isEqualTo(cookingLog.memberId.eq(memberId));
        assertThat(predicates[1]).isEqualTo(recipeReview.id.isNotNull());

        // Then: orderBy 정렬 확인
        ArgumentCaptor<OrderSpecifier[]> orderCaptor = ArgumentCaptor.forClass(OrderSpecifier[].class);
        verify(myRecipeQuery).orderBy(orderCaptor.capture());
        OrderSpecifier[] orderSpecifiers = orderCaptor.getValue();
        assertThat(orderSpecifiers[0]).isEqualTo(cookingLog.count().desc());
        assertThat(orderSpecifiers[1]).isEqualTo(cookingLog.createdAt.max().desc());
        assertThat(orderSpecifiers[2]).isEqualTo(cookingLog.recipeId.desc());
    }

    /**
     * reviewStatus=NOT_WRITTEN 조회 시 where 절에 recipeReview.id IS NULL 조건이 적용되는지 검증한다.
     */
    @Test
    @DisplayName("후기 미작성 레시피를 조회하면 후기 없음 조건을 넣는다")
    void filtersNotWritten_whenStatusIsNotWritten() {
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
        assertThat(predicates[1]).isEqualTo(recipeReview.id.isNull());
    }

    /**
     * getMyRecipesSummaryCount 단일 쿼리로 recipeCount, totalCookCount, writtenCount를 조회하고
     * notWrittenCount(recipeCount - writtenCount)가 자동 산출되는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피 요약은 한 번의 쿼리로 세고 후기 미작성 수는 레시피 수에서 작성 수를 빼서 구한다")
    void calculatesSummaryCountsWithSingleQuery() {
        // Given: 레시피 3개, 총 요리 7회, 후기 2건 작성 -> 미작성 1건
        Long memberId = 7L;
        MyRecipeSummaryCount expectedSummary = new MyRecipeSummaryCount(3L, 7L, 2L);

        when(queryFactory.select(any(Expression.class))).thenReturn(summaryQuery);
        when(summaryQuery.from(cookingLog)).thenReturn(summaryQuery);
        when(summaryQuery.leftJoin(recipeReview)).thenReturn(summaryQuery);
        when(summaryQuery.on(recipeReview.member.id.eq(cookingLog.memberId)
                .and(recipeReview.recipeId.eq(cookingLog.recipeId)))).thenReturn(summaryQuery);
        when(summaryQuery.where(cookingLog.memberId.eq(memberId))).thenReturn(summaryQuery);
        when(summaryQuery.fetchOne()).thenReturn(expectedSummary);

        // When
        MyRecipeSummaryCount summary = repository.getMyRecipesSummaryCount(memberId);

        // Then: 조인 및 조건 호출 확인과 자동 계산된 notWrittenCount 확인
        assertThat(summary.recipeCount()).isEqualTo(3L);
        assertThat(summary.totalCookCount()).isEqualTo(7L);
        assertThat(summary.writtenCount()).isEqualTo(2L);
        assertThat(summary.notWrittenCount()).isEqualTo(1L); // 3 - 2 = 1

        verify(summaryQuery).leftJoin(recipeReview);
        verify(summaryQuery).where(cookingLog.memberId.eq(memberId));
    }

    /**
     * findMostCookedRecipe 호출 시 query 조립과 결과 DTO 반환을 검증한다.
     */
    @Test
    @DisplayName("가장 많이 만든 레시피를 조회하면 쿼리 결과를 그대로 반환한다")
    void returnsMostCookedRecipe() {
        Long memberId = 7L;
        MostCookedRecipeDto expected = new MostCookedRecipeDto(1L, 5L);

        when(queryFactory.select(any(Expression.class))).thenReturn(mostCookedQuery);
        when(mostCookedQuery.from(cookingLog)).thenReturn(mostCookedQuery);
        when(mostCookedQuery.where(cookingLog.memberId.eq(memberId))).thenReturn(mostCookedQuery);
        when(mostCookedQuery.groupBy(cookingLog.recipeId)).thenReturn(mostCookedQuery);
        when(mostCookedQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(mostCookedQuery);
        when(mostCookedQuery.limit(1L)).thenReturn(mostCookedQuery);
        when(mostCookedQuery.fetchOne()).thenReturn(expected);

        Optional<MostCookedRecipeDto> result = repository.findMostCookedRecipe(memberId);

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().recipeId()).isEqualTo(1L);
        assertThat(result.get().cookCount()).isEqualTo(5L);
    }

    /**
     * getCookingStats 호출 시 recipeId가 null이면
     * 쿼리를 실행하지 않고 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    @DisplayName("요리 통계 조회에서 레시피 ID가 없으면 쿼리를 실행하지 않고 예외를 던진다")
    void throwsIllegalArgument_whenRecipeIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> repository.getCookingStats(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(queryFactory, never()).select(any(Expression.class));
    }

    /**
     * getCookingStats 호출 시 cookingLog를 대상으로 단일 집계 쿼리를 조립하고
     * 결과 DTO에 recipeId와 집계 카운트를 결합하여 반환하는지 검증한다.
     */
    @Test
    @DisplayName("요리 통계는 한 번의 집계 쿼리로 구해 레시피 ID와 함께 반환한다")
    void returnsStatsWithSingleQuery() {
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
        assertThat(response.recipeId()).isEqualTo(recipeId);
        assertThat(response.totalCount()).isEqualTo(10L);
        assertThat(response.successCount()).isEqualTo(7L);
        assertThat(response.failedCount()).isEqualTo(3L);

        verify(statsCountQuery).from(cookingLog);
        verify(statsCountQuery).where(cookingLog.recipeId.eq(recipeId));
    }

    /**
     * getCookingStats 호출 시 쿼리 결과가 null이면 모든 카운트가 0인 응답을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("요리 통계 쿼리 결과가 없으면 모든 횟수를 0으로 반환한다")
    void returnsZeroStats_whenQueryResultIsNull() {
        // Given
        Long recipeId = 12L;

        when(queryFactory.select(any(Expression.class))).thenReturn(statsCountQuery);
        when(statsCountQuery.from(cookingLog)).thenReturn(statsCountQuery);
        when(statsCountQuery.where(cookingLog.recipeId.eq(recipeId))).thenReturn(statsCountQuery);
        when(statsCountQuery.fetchOne()).thenReturn(null);

        // When
        CookingStatsResponse response = repository.getCookingStats(recipeId);

        // Then
        assertThat(response.recipeId()).isEqualTo(recipeId);
        assertThat(response.totalCount()).isEqualTo(0L);
        assertThat(response.successCount()).isEqualTo(0L);
        assertThat(response.failedCount()).isEqualTo(0L);
    }
}

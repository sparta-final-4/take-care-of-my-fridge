package org.example.takecareofmyfridgebe.domain.cooking.service;

import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogUpdateRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeDto;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeListResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CookingService 단위 테스트.
 * 요리 기록 저장 및 조회 조건(recipeId, from, to)과 DTO 변환(fromChat 여부),
 * from > to 유효성 검증, 내 레시피 페이징 및 요약 통계 조립,
 * 요리 기록 수정(결과·메모) 및 더티 체킹, 타인/미존재 시 404 예외 처리,
 * 레시피 요리 실패/성공 통계 조회를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class CookingServiceTest {

    @Mock
    private CookingLogRepository cookingLogRepository;

    @Mock
    private RecipeService recipeService;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private CookingService cookingService;

    /**
     * 정상적인 요리 기록 저장 요청 시 엔티티를 repository.save로 저장하는지 검증한다.
     */
    @Test
    void saveCookingLogSavesEntity() {
        // Given
        Long memberId = 7L;
        CookingCompleteRequest request = new CookingCompleteRequest(
                12L, CookingResult.SUCCESS, "맛있어요", 10L, List.of());
        CookingLog savedLog = request.toEntity(memberId);
        ReflectionTestUtils.setField(savedLog, "id", 100L);

        when(recipeService.existsById(12L)).thenReturn(true);
        when(cookingLogRepository.save(any(CookingLog.class))).thenReturn(savedLog);

        // When
        CookingLog result = cookingService.saveCookingLog(memberId, request);

        // Then
        assertEquals(100L, result.getId());
        verify(recipeService).existsById(12L);
        verify(cookingLogRepository).save(any(CookingLog.class));
    }

    /**
     * 요리 완료 요청 시 존재하지 않는 레시피이면 404 RECIPE_NOT_FOUND 예외가 발생한다.
     */
    @Test
    void saveCookingLogThrowsWhenRecipeNotFound() {
        // Given
        Long memberId = 7L;
        CookingCompleteRequest request = new CookingCompleteRequest(
                999L, CookingResult.SUCCESS, null, null, List.of());
        when(recipeService.existsById(999L)).thenReturn(false);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingService.saveCookingLog(memberId, request)
        );
        assertEquals(ErrorCode.RECIPE_NOT_FOUND, exception.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getErrorCode().getStatus());
        verify(cookingLogRepository, never()).save(any());
    }

    /**
     * getCookingLogs 호출 시 정상 조건이면 repository 조회 결과를 CookingLogResponse DTO로 변환하여 반환한다.
     * chatSessionId가 존재하면 fromChat=true, 없으면 false로 변환된다.
     */
    @Test
    void getCookingLogsReturnsPagedResponse() {
        // Given
        Long memberId = 7L;
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                12L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), 1, 10);

        CookingLog log1 = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(5L)
                .result(CookingResult.SUCCESS)
                .memo("성공 메모")
                .build();
        ReflectionTestUtils.setField(log1, "id", 101L);

        CookingLog log2 = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(null)
                .result(CookingResult.FAILED)
                .memo("실패 메모")
                .build();
        ReflectionTestUtils.setField(log2, "id", 102L);

        Page<CookingLog> page = new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 10), 2L);
        when(cookingLogRepository.findLogsByCondition(
                eq(memberId), eq(12L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 15)),
                eq(PageRequest.of(0, 10))
        )).thenReturn(page);

        // When
        PageResponse<CookingLogResponse> response = cookingService.getCookingLogs(memberId, request);

        // Then
        assertEquals(2, response.content().size());
        assertEquals(1, response.pageNumber());
        assertEquals(10, response.pageSize());
        assertEquals(2L, response.totalElements());

        CookingLogResponse dto1 = response.content().get(0);
        assertEquals(101L, dto1.id());
        assertEquals(12L, dto1.recipeId());
        assertTrue(dto1.fromChat());
        assertEquals(CookingResult.SUCCESS, dto1.result());
        assertEquals("성공 메모", dto1.memo());

        CookingLogResponse dto2 = response.content().get(1);
        assertEquals(102L, dto2.id());
        assertEquals(12L, dto2.recipeId());
        assertFalse(dto2.fromChat());
        assertEquals(CookingResult.FAILED, dto2.result());
        assertEquals("실패 메모", dto2.memo());
    }

    /**
     * getCookingLogs 호출 시 시작일(from)이 종료일(to)보다 이후이면 INVALID_INPUT_VALUE 예외가 발생한다.
     */
    @Test
    void getCookingLogsThrowsWhenFromIsAfterTo() {
        // Given
        Long memberId = 7L;
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                null, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), 1, 10);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingService.getCookingLogs(memberId, request)
        );
        assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getErrorCode().getStatus());
        verifyNoInteractions(cookingLogRepository);
    }

    /**
     * getMyRecipes 호출 시 repository에서 조회된 레시피 페이징 결과와 전체 요약 통계(summary)를 조합하여 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesReturnsSummaryAndPagedRecipes() {
        // Given: 레시피 목록 및 요약 통계 준비
        Long memberId = 7L;
        MyRecipeSearchRequest request = new MyRecipeSearchRequest(
                MyRecipeReviewStatus.WRITTEN, MyRecipeSortType.MOST, 2, 5);

        MyRecipeResponse recipe = new MyRecipeResponse(
                1L, "불닭 참치 두부조림", "https://image.com/tofu.jpg", 3L,
                LocalDateTime.of(2026, 10, 12, 18, 0), true,
                new MyRecipeReviewResponse(101L, 5, "맛있어요", LocalDateTime.of(2026, 10, 12, 19, 0))
        );
        Page<MyRecipeResponse> recipePage = new PageImpl<>(List.of(recipe), PageRequest.of(1, 5), 6L);
        MyRecipeSummaryCount summaryCount = new MyRecipeSummaryCount(4L, 10L, 3L, 1L);

        when(cookingLogRepository.findMyRecipes(
                eq(memberId), eq(MyRecipeReviewStatus.WRITTEN), eq(MyRecipeSortType.MOST),
                eq(PageRequest.of(1, 5))
        )).thenReturn(recipePage);

        when(cookingLogRepository.getMyRecipesSummaryCount(eq(memberId)))
                .thenReturn(summaryCount);
        when(cookingLogRepository.findMostCookedRecipe(eq(memberId)))
                .thenReturn(Optional.of(new MostCookedRecipeDto(1L, 3L)));
        when(recipeService.findSummaries(eq(List.of(1L))))
                .thenReturn(Map.of(1L, new RecipeSummary(1L, "불닭 참치 두부조림", "https://image.com/tofu.jpg")));
        when(fridgeService.countConsumedByCooking(eq(memberId)))
                .thenReturn(5L);

        // When: 서비스 메서드 호출
        MyRecipeListResponse response = cookingService.getMyRecipes(memberId, request);

        // Then: 요약 통계(summary) 및 페이징된 레시피 목록(recipes) 결합 확인
        assertEquals(4L, response.summary().recipeCount());
        assertEquals(10L, response.summary().totalCookCount());
        assertEquals(3L, response.summary().writtenCount());
        assertEquals(1L, response.summary().notWrittenCount());
        assertNotNull(response.summary().mostCookedRecipe());
        assertEquals(1L, response.summary().mostCookedRecipe().recipeId());
        assertEquals("불닭 참치 두부조림", response.summary().mostCookedRecipe().recipeName());
        assertEquals(3L, response.summary().mostCookedRecipe().cookCount());
        assertEquals(5L, response.summary().consumedIngredientCount());

        PageResponse<MyRecipeResponse> recipes = response.recipes();
        assertEquals(2, recipes.pageNumber());
        assertEquals(5, recipes.pageSize());
        assertEquals(6L, recipes.totalElements());
        assertEquals(2, recipes.totalPages());
        assertEquals(1, recipes.content().size());

        MyRecipeResponse item = recipes.content().get(0);
        assertEquals(1L, item.recipeId());
        assertEquals("불닭 참치 두부조림", item.recipeName());
        assertTrue(item.fromChat());
        assertNotNull(item.review());
        assertEquals(101L, item.review().id());

        verify(cookingLogRepository).findMyRecipes(
                eq(memberId), eq(MyRecipeReviewStatus.WRITTEN), eq(MyRecipeSortType.MOST), eq(PageRequest.of(1, 5)));
        verify(cookingLogRepository).getMyRecipesSummaryCount(eq(memberId));
        verify(cookingLogRepository).findMostCookedRecipe(eq(memberId));
        verify(recipeService).findSummaries(eq(List.of(1L)));
        verify(fridgeService).countConsumedByCooking(eq(memberId));
    }

    /**
     * 요리 기록이 없는 회원의 경우 빈 페이지와 0으로 초기화된 요약 통계(mostCookedRecipe=null, consumedIngredientCount=0)를 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesWithEmptyResultReturnsZeroSummaryAndEmptyPage() {
        // Given: 빈 레시피 페이지와 0으로 채워진 요약 통계 준비
        Long memberId = 7L;
        MyRecipeSearchRequest request = new MyRecipeSearchRequest(
                MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, 1, 10);

        Page<MyRecipeResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        MyRecipeSummaryCount zeroSummary = new MyRecipeSummaryCount(0L, 0L, 0L, 0L);

        when(cookingLogRepository.findMyRecipes(
                eq(memberId), eq(MyRecipeReviewStatus.ALL), eq(MyRecipeSortType.RECENT),
                eq(PageRequest.of(0, 10))
        )).thenReturn(emptyPage);

        when(cookingLogRepository.getMyRecipesSummaryCount(eq(memberId)))
                .thenReturn(zeroSummary);
        when(cookingLogRepository.findMostCookedRecipe(eq(memberId)))
                .thenReturn(Optional.empty());
        when(fridgeService.countConsumedByCooking(eq(memberId)))
                .thenReturn(0L);

        // When: 서비스 메서드 호출
        MyRecipeListResponse response = cookingService.getMyRecipes(memberId, request);

        // Then: 요약 통계가 0이고 레시피 목록이 비어 있는지 확인
        assertEquals(0L, response.summary().recipeCount());
        assertEquals(0L, response.summary().totalCookCount());
        assertEquals(0L, response.summary().writtenCount());
        assertEquals(0L, response.summary().notWrittenCount());
        assertNull(response.summary().mostCookedRecipe());
        assertEquals(0L, response.summary().consumedIngredientCount());
        assertTrue(response.recipes().content().isEmpty());
        assertEquals(0L, response.recipes().totalElements());
    }

    /**
     * 레시피 요약 정보(RecipeService.findSummaries)에서 레시피를 찾을 수 없는 경우,
     * mostCookedRecipe의 recipeName이 빈 문자열이 아닌 null로 설정되는지 검증한다.
     */
    @Test
    void getMyRecipesWhenRecipeSummaryMissingReturnsNullRecipeName() {
        Long memberId = 7L;
        MyRecipeSearchRequest request = new MyRecipeSearchRequest(
                MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, 1, 10);
        Page<MyRecipeResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        MyRecipeSummaryCount summaryCount = new MyRecipeSummaryCount(1L, 2L, 0L, 1L);

        when(cookingLogRepository.findMyRecipes(any(), any(), any(), any())).thenReturn(emptyPage);
        when(cookingLogRepository.getMyRecipesSummaryCount(memberId)).thenReturn(summaryCount);
        when(cookingLogRepository.findMostCookedRecipe(memberId))
                .thenReturn(Optional.of(new MostCookedRecipeDto(999L, 2L)));
        when(recipeService.findSummaries(List.of(999L))).thenReturn(Map.of());
        when(fridgeService.countConsumedByCooking(memberId)).thenReturn(1L);

        MyRecipeListResponse response = cookingService.getMyRecipes(memberId, request);

        assertNotNull(response.summary().mostCookedRecipe());
        assertEquals(999L, response.summary().mostCookedRecipe().recipeId());
        assertNull(response.summary().mostCookedRecipe().recipeName());
        assertEquals(2L, response.summary().mostCookedRecipe().cookCount());
        assertEquals(1L, response.summary().consumedIngredientCount());
    }

    /**
     * 요리 기록 수정 시 엔티티의 결과와 메모만 변경되고,
     * repository.save()를 호출하지 않고 JPA 더티 체킹에 위임하는지 검증한다.
     */
    @Test
    void updateCookingLogModifiesResultAndMemoWithDirtyChecking() {
        // Given
        Long memberId = 7L;
        Long cookingLogId = 100L;
        CookingLog cookingLog = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(5L)
                .result(CookingResult.SUCCESS)
                .memo("처음 작성한 메모")
                .build();
        ReflectionTestUtils.setField(cookingLog, "id", cookingLogId);

        when(cookingLogRepository.findByIdAndMemberId(cookingLogId, memberId))
                .thenReturn(Optional.of(cookingLog));

        CookingLogUpdateRequest request = new CookingLogUpdateRequest(CookingResult.FAILED, "수정된 메모");

        // When
        CookingLogResponse response = cookingService.updateCookingLog(memberId, cookingLogId, request);

        // Then
        assertEquals(cookingLogId, response.id());
        assertEquals(CookingResult.FAILED, response.result());
        assertEquals("수정된 메모", response.memo());
        assertEquals(CookingResult.FAILED, cookingLog.getResult());
        assertEquals("수정된 메모", cookingLog.getMemo());

        // 더티 체킹 검증: save()를 호출하지 않음
        verify(cookingLogRepository, never()).save(any(CookingLog.class));
    }

    /**
     * 존재하지 않거나 다른 회원의 요리 기록인 경우
     * 404 COOKING_LOG_NOT_FOUND 예외를 던지는지 검증한다.
     */
    @Test
    void updateCookingLogThrowsWhenNotFoundOrDifferentMember() {
        // Given
        Long memberId = 7L;
        Long cookingLogId = 999L;
        when(cookingLogRepository.findByIdAndMemberId(cookingLogId, memberId))
                .thenReturn(Optional.empty());

        CookingLogUpdateRequest request = new CookingLogUpdateRequest(CookingResult.SUCCESS, "메모");

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingService.updateCookingLog(memberId, cookingLogId, request)
        );
        assertEquals(ErrorCode.COOKING_LOG_NOT_FOUND, exception.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getErrorCode().getStatus());
        verify(cookingLogRepository, never()).save(any(CookingLog.class));
    }

    /**
     * 요리 기록을 수정해도 재료 차감 이력 등 다른 연관 데이터(recipeId, chatSessionId, memberId)는
     * 일체 변경되지 않고 결과와 메모만 수정되는지 검증한다.
     * (재료 차감과 묶인 값은 되돌리기 어려워 결과와 메모만 수정)
     */
    @Test
    void updateCookingLogPreservesRecipeChatSessionAndIngredientsDeductionState() {
        // Given
        Long memberId = 7L;
        Long cookingLogId = 100L;
        Long recipeId = 12L;
        Long chatSessionId = 5L;

        CookingLog cookingLog = CookingLog.builder()
                .memberId(memberId)
                .recipeId(recipeId)
                .chatSessionId(chatSessionId)
                .result(CookingResult.SUCCESS)
                .memo("소금 조금 더 넣을 걸")
                .build();
        ReflectionTestUtils.setField(cookingLog, "id", cookingLogId);

        when(cookingLogRepository.findByIdAndMemberId(cookingLogId, memberId))
                .thenReturn(Optional.of(cookingLog));

        CookingLogUpdateRequest request = new CookingLogUpdateRequest(CookingResult.FAILED, null);

        // When
        CookingLogResponse response = cookingService.updateCookingLog(memberId, cookingLogId, request);

        // Then: 결과와 메모만 변경되고 차감/상담 식별자는 그대로 유지됨
        assertEquals(CookingResult.FAILED, response.result());
        assertNull(response.memo());
        assertEquals(recipeId, response.recipeId());
        assertTrue(response.fromChat());

        // 엔티티 내부 필드 보존 검증
        assertEquals(memberId, cookingLog.getMemberId());
        assertEquals(recipeId, cookingLog.getRecipeId());
        assertEquals(chatSessionId, cookingLog.getChatSessionId());
    }

    /**
     * 레시피 요리 통계 조회 시 repository.getCookingStats(recipeId)를 호출하여 반환하는지 검증한다.
     */
    @Test
    void getCookingStatsDelegatesToRepository() {
        // Given
        Long recipeId = 12L;
        CookingStatsResponse expected = new CookingStatsResponse(recipeId, 10L, 7L, 3L);
        when(recipeService.existsById(recipeId)).thenReturn(true);
        when(cookingLogRepository.getCookingStats(recipeId)).thenReturn(expected);

        // When
        CookingStatsResponse actual = cookingService.getCookingStats(recipeId);

        // Then
        assertEquals(expected, actual);
        assertEquals(recipeId, actual.recipeId());
        assertEquals(10L, actual.totalCount());
        assertEquals(7L, actual.successCount());
        assertEquals(3L, actual.failedCount());
        verify(recipeService).existsById(recipeId);
        verify(cookingLogRepository).getCookingStats(recipeId);
    }

    /**
     * 레시피 요리 통계 조회 시 존재하지 않는 레시피이면 404 RECIPE_NOT_FOUND 예외가 발생한다.
     */
    @Test
    void getCookingStatsThrowsWhenRecipeNotFound() {
        // Given
        Long recipeId = 999L;
        when(recipeService.existsById(recipeId)).thenReturn(false);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingService.getCookingStats(recipeId)
        );
        assertEquals(ErrorCode.RECIPE_NOT_FOUND, exception.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getErrorCode().getStatus());
        verify(cookingLogRepository, never()).getCookingStats(any());
    }

    /**
     * 특정 레시피 요리 완료 여부 조회 시 repository.existsByMemberIdAndRecipeId를 호출하여 반환한다.
     */
    @Test
    void hasCookedDelegatesToRepository() {
        // Given
        Long memberId = 1L;
        Long recipeId = 10L;
        when(cookingLogRepository.existsByMemberIdAndRecipeId(memberId, recipeId)).thenReturn(true);

        // When
        boolean actual = cookingService.hasCooked(memberId, recipeId);

        // Then
        assertTrue(actual);
        verify(cookingLogRepository).existsByMemberIdAndRecipeId(memberId, recipeId);
    }
}

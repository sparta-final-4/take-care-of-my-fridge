package org.example.takecareofmyfridgebe.domain.cooking.service;

import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeListResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CookingService 단위 테스트.
 * 요리 기록 저장 및 조회 조건(recipeId, from, to)과 DTO 변환(fromChat 여부),
 * from > to 유효성 검증, 내 레시피 페이징 및 요약 통계 조립을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class CookingServiceTest {

    @Mock
    private CookingLogRepository cookingLogRepository;

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

        when(cookingLogRepository.save(any(CookingLog.class))).thenReturn(savedLog);

        // When
        CookingLog result = cookingService.saveCookingLog(memberId, request);

        // Then
        assertEquals(100L, result.getId());
        verify(cookingLogRepository).save(any(CookingLog.class));
    }

    /**
     * 조회 기간의 시작일(from)이 종료일(to)보다 늦으면
     * 400 INVALID_INPUT_VALUE 예외가 발생하고 repository 조회를 호출하지 않는지 검증한다.
     */
    @Test
    void getCookingLogsThrowsWhenFromIsAfterTo() {
        // Given
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                null, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), 1, 20);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingService.getCookingLogs(7L, request)
        );
        assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getErrorCode().getStatus());
        verifyNoInteractions(cookingLogRepository);
    }

    /**
     * 요리 기록 조회 시:
     * 1) 조건(recipeId, from, to)과 1부터 시작하는 page를 0부터 시작하는 PageRequest로 넘기는지
     * 2) chatSessionId 존재 여부에 따라 fromChat이 올바르게 매핑되는지 검증한다.
     */
    @Test
    void getCookingLogsMapsEntitiesToResponsesWithFromChatFlag() {
        // Given
        Long memberId = 7L;
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                12L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1, 20);

        CookingLog logWithChat = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(5L)
                .result(CookingResult.SUCCESS)
                .memo("상담 후 요리")
                .build();
        ReflectionTestUtils.setField(logWithChat, "id", 101L);
        ReflectionTestUtils.setField(logWithChat, "createdAt", LocalDateTime.of(2026, 10, 15, 12, 0));

        CookingLog logWithoutChat = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(null)
                .result(CookingResult.FAILED)
                .memo("직접 요리")
                .build();
        ReflectionTestUtils.setField(logWithoutChat, "id", 102L);
        ReflectionTestUtils.setField(logWithoutChat, "createdAt", LocalDateTime.of(2026, 10, 16, 18, 0));

        Page<CookingLog> page = new PageImpl<>(List.of(logWithChat, logWithoutChat));

        when(cookingLogRepository.findLogsByCondition(
                eq(memberId), eq(12L),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)),
                eq(PageRequest.of(0, 20))
        )).thenReturn(page);

        // When
        PageResponse<CookingLogResponse> response = cookingService.getCookingLogs(memberId, request);

        // Then
        assertEquals(2, response.content().size());

        CookingLogResponse first = response.content().get(0);
        assertEquals(101L, first.id());
        assertTrue(first.fromChat());
        assertEquals(CookingResult.SUCCESS, first.result());
        assertEquals("상담 후 요리", first.memo());

        CookingLogResponse second = response.content().get(1);
        assertEquals(102L, second.id());
        assertFalse(second.fromChat());
        assertEquals(CookingResult.FAILED, second.result());
    }

    /**
     * 내 레시피 목록 조회 시 1-based page를 0-based PageRequest로 변환하고
     * 레시피 목록과 전체 요약 통계를 결합해 MyRecipeListResponse로 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesReturnsSummaryAndPageResponse() {
        // Given: 회원 ID, 요청 파라미터(page=2, size=5), 모킹용 레시피 페이지 및 요약 통계 준비
        Long memberId = 7L;
        MyRecipeSearchRequest request = new MyRecipeSearchRequest(
                MyRecipeReviewStatus.WRITTEN, MyRecipeSortType.MOST, 2, 5);

        MyRecipeResponse recipe = new MyRecipeResponse(
                1L, "불닭 참치 두부조림", "https://image.com/tofu.jpg", 3L,
                LocalDateTime.of(2026, 10, 12, 18, 0), true,
                new MyRecipeReviewResponse(101L, 5, "맛있어요", LocalDateTime.of(2026, 10, 12, 19, 0))
        );
        Page<MyRecipeResponse> recipePage = new PageImpl<>(List.of(recipe), PageRequest.of(1, 5), 6L);
        MyRecipeSummaryResponse summary = new MyRecipeSummaryResponse(4L, 10L, 3L, 1L);

        when(cookingLogRepository.findMyRecipes(
                eq(memberId), eq(MyRecipeReviewStatus.WRITTEN), eq(MyRecipeSortType.MOST),
                eq(PageRequest.of(1, 5))
        )).thenReturn(recipePage);

        when(cookingLogRepository.getMyRecipesSummary(eq(memberId)))
                .thenReturn(summary);

        // When: 서비스 메서드 호출
        MyRecipeListResponse response = cookingService.getMyRecipes(memberId, request);

        // Then: 요약 통계(summary) 및 페이징된 레시피 목록(recipes) 결합 확인
        assertEquals(summary, response.summary());
        assertEquals(4L, response.summary().recipeCount());
        assertEquals(10L, response.summary().totalCookCount());
        assertEquals(3L, response.summary().writtenCount());
        assertEquals(1L, response.summary().notWrittenCount());

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
        verify(cookingLogRepository).getMyRecipesSummary(eq(memberId));
    }

    /**
     * 요리 기록이 없는 회원의 경우 빈 페이지와 0으로 초기화된 요약 통계를 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesWithEmptyResultReturnsZeroSummaryAndEmptyPage() {
        // Given: 빈 레시피 페이지와 0으로 채워진 요약 통계 준비
        Long memberId = 7L;
        MyRecipeSearchRequest request = new MyRecipeSearchRequest(
                MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, 1, 10);

        Page<MyRecipeResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        MyRecipeSummaryResponse zeroSummary = new MyRecipeSummaryResponse(0L, 0L, 0L, 0L);

        when(cookingLogRepository.findMyRecipes(
                eq(memberId), eq(MyRecipeReviewStatus.ALL), eq(MyRecipeSortType.RECENT),
                eq(PageRequest.of(0, 10))
        )).thenReturn(emptyPage);

        when(cookingLogRepository.getMyRecipesSummary(eq(memberId)))
                .thenReturn(zeroSummary);

        // When: 서비스 메서드 호출
        MyRecipeListResponse response = cookingService.getMyRecipes(memberId, request);

        // Then: 요약 통계가 0이고 레시피 목록이 비어 있는지 확인
        assertEquals(0L, response.summary().recipeCount());
        assertEquals(0L, response.summary().totalCookCount());
        assertEquals(0L, response.summary().writtenCount());
        assertEquals(0L, response.summary().notWrittenCount());
        assertTrue(response.recipes().content().isEmpty());
        assertEquals(0L, response.recipes().totalElements());
    }
}

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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("요리 기록 저장을 요청하면 요리 기록을 저장한다")
    void savesCookingLog() {
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
        assertThat(result.getId()).isEqualTo(100L);
        verify(recipeService).existsById(12L);
        verify(cookingLogRepository).save(any(CookingLog.class));
    }

    /**
     * 요리 완료 요청 시 존재하지 않는 레시피이면 404 RECIPE_NOT_FOUND 예외가 발생한다.
     */
    @Test
    @DisplayName("없는 레시피로 요리 기록을 저장하면 RECIPE_NOT_FOUND 예외를 던지고 저장하지 않는다")
    void throwsRecipeNotFound_whenSavingWithMissingRecipe() {
        // Given
        Long memberId = 7L;
        CookingCompleteRequest request = new CookingCompleteRequest(
                999L, CookingResult.SUCCESS, null, null, List.of());
        when(recipeService.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> cookingService.saveCookingLog(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPE_NOT_FOUND)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(cookingLogRepository, never()).save(any());
    }

    /**
     * getCookingLogs 호출 시 정상 조건이면 repository 조회 결과를 CookingLogResponse DTO로 변환하여 반환한다.
     * chatSessionId가 존재하면 fromChat=true, 없으면 false로 변환된다.
     */
    @Test
    @DisplayName("요리 기록을 조회하면 상담 여부를 담은 응답 페이지로 변환한다")
    void returnsLogsWithFromChatFlag() {
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
        assertThat(response.content().size()).isEqualTo(2);
        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(2L);

        CookingLogResponse dto1 = response.content().get(0);
        assertThat(dto1.id()).isEqualTo(101L);
        assertThat(dto1.recipeId()).isEqualTo(12L);
        assertThat(dto1.fromChat()).isTrue();
        assertThat(dto1.result()).isEqualTo(CookingResult.SUCCESS);
        assertThat(dto1.memo()).isEqualTo("성공 메모");

        CookingLogResponse dto2 = response.content().get(1);
        assertThat(dto2.id()).isEqualTo(102L);
        assertThat(dto2.recipeId()).isEqualTo(12L);
        assertThat(dto2.fromChat()).isFalse();
        assertThat(dto2.result()).isEqualTo(CookingResult.FAILED);
        assertThat(dto2.memo()).isEqualTo("실패 메모");
    }

    /**
     * getCookingLogs 호출 시 시작일(from)이 종료일(to)보다 이후이면 INVALID_INPUT_VALUE 예외가 발생한다.
     */
    @Test
    @DisplayName("요리 기록 조회 시작일이 종료일보다 늦으면 INVALID_INPUT_VALUE 예외를 던지고 조회하지 않는다")
    void throwsInvalidInput_whenFromIsAfterTo() {
        // Given
        Long memberId = 7L;
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                null, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), 1, 10);

        // When & Then
        assertThatThrownBy(() -> cookingService.getCookingLogs(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(cookingLogRepository);
    }

    /**
     * getMyRecipes 호출 시 repository에서 조회된 레시피 페이징 결과와 전체 요약 통계(summary)를 조합하여 반환하는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피를 조회하면 요약 통계와 레시피 목록을 함께 반환한다")
    void returnsSummaryAndRecipes() {
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
                .thenReturn(Map.of(1L, new RecipeSummary(1L, "불닭 참치 두부조림", "https://image.com/tofu.jpg", null)));
        when(fridgeService.countConsumedByCooking(eq(memberId)))
                .thenReturn(5L);

        // When: 서비스 메서드 호출
        MyRecipeListResponse response = cookingService.getMyRecipes(memberId, request);

        // Then: 요약 통계(summary) 및 페이징된 레시피 목록(recipes) 결합 확인
        assertThat(response.summary().recipeCount()).isEqualTo(4L);
        assertThat(response.summary().totalCookCount()).isEqualTo(10L);
        assertThat(response.summary().writtenCount()).isEqualTo(3L);
        assertThat(response.summary().notWrittenCount()).isEqualTo(1L);
        assertThat(response.summary().mostCookedRecipe()).isNotNull();
        assertThat(response.summary().mostCookedRecipe().recipeId()).isEqualTo(1L);
        assertThat(response.summary().mostCookedRecipe().recipeName()).isEqualTo("불닭 참치 두부조림");
        assertThat(response.summary().mostCookedRecipe().cookCount()).isEqualTo(3L);
        assertThat(response.summary().consumedIngredientCount()).isEqualTo(5L);

        PageResponse<MyRecipeResponse> recipes = response.recipes();
        assertThat(recipes.pageNumber()).isEqualTo(2);
        assertThat(recipes.pageSize()).isEqualTo(5);
        assertThat(recipes.totalElements()).isEqualTo(6L);
        assertThat(recipes.totalPages()).isEqualTo(2);
        assertThat(recipes.content().size()).isEqualTo(1);

        MyRecipeResponse item = recipes.content().get(0);
        assertThat(item.recipeId()).isEqualTo(1L);
        assertThat(item.recipeName()).isEqualTo("불닭 참치 두부조림");
        assertThat(item.fromChat()).isTrue();
        assertThat(item.review()).isNotNull();
        assertThat(item.review().id()).isEqualTo(101L);

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
    @DisplayName("요리 기록이 없는 회원은 빈 목록과 0으로 채운 요약을 받는다")
    void returnsZeroSummary_whenNoCookingLogs() {
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
        assertThat(response.summary().recipeCount()).isEqualTo(0L);
        assertThat(response.summary().totalCookCount()).isEqualTo(0L);
        assertThat(response.summary().writtenCount()).isEqualTo(0L);
        assertThat(response.summary().notWrittenCount()).isEqualTo(0L);
        assertThat(response.summary().mostCookedRecipe()).isNull();
        assertThat(response.summary().consumedIngredientCount()).isEqualTo(0L);
        assertThat(response.recipes().content().isEmpty()).isTrue();
        assertThat(response.recipes().totalElements()).isEqualTo(0L);
    }

    /**
     * 레시피 요약 정보(RecipeService.findSummaries)에서 레시피를 찾을 수 없는 경우,
     * mostCookedRecipe의 recipeName이 빈 문자열이 아닌 null로 설정되는지 검증한다.
     */
    @Test
    @DisplayName("가장 많이 만든 레시피의 정보를 찾지 못하면 레시피 이름을 null로 반환한다")
    void returnsNullRecipeName_whenRecipeSummaryIsMissing() {
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

        assertThat(response.summary().mostCookedRecipe()).isNotNull();
        assertThat(response.summary().mostCookedRecipe().recipeId()).isEqualTo(999L);
        assertThat(response.summary().mostCookedRecipe().recipeName()).isNull();
        assertThat(response.summary().mostCookedRecipe().cookCount()).isEqualTo(2L);
        assertThat(response.summary().consumedIngredientCount()).isEqualTo(1L);
    }

    /**
     * 요리 기록 수정 시 엔티티의 결과와 메모만 변경되고,
     * repository.save()를 호출하지 않고 JPA 더티 체킹에 위임하는지 검증한다.
     */
    @Test
    @DisplayName("요리 기록을 수정하면 save 호출 없이 결과와 메모만 바뀐다")
    void updatesResultAndMemo() {
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
        assertThat(response.id()).isEqualTo(cookingLogId);
        assertThat(response.result()).isEqualTo(CookingResult.FAILED);
        assertThat(response.memo()).isEqualTo("수정된 메모");
        assertThat(cookingLog.getResult()).isEqualTo(CookingResult.FAILED);
        assertThat(cookingLog.getMemo()).isEqualTo("수정된 메모");

        // 더티 체킹 검증: save()를 호출하지 않음
        verify(cookingLogRepository, never()).save(any(CookingLog.class));
    }

    /**
     * 존재하지 않거나 다른 회원의 요리 기록인 경우
     * 404 COOKING_LOG_NOT_FOUND 예외를 던지는지 검증한다.
     */
    @Test
    @DisplayName("다른 회원의 요리 기록이거나 없는 기록을 수정하면 COOKING_LOG_NOT_FOUND 예외를 던진다")
    void throwsCookingLogNotFound_whenLogIsOthersOrMissing() {
        // Given
        Long memberId = 7L;
        Long cookingLogId = 999L;
        when(cookingLogRepository.findByIdAndMemberId(cookingLogId, memberId))
                .thenReturn(Optional.empty());

        CookingLogUpdateRequest request = new CookingLogUpdateRequest(CookingResult.SUCCESS, "메모");

        // When & Then
        assertThatThrownBy(() -> cookingService.updateCookingLog(memberId, cookingLogId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COOKING_LOG_NOT_FOUND)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(cookingLogRepository, never()).save(any(CookingLog.class));
    }

    /**
     * 요리 기록을 수정해도 재료 차감 이력 등 다른 연관 데이터(recipeId, chatSessionId, memberId)는
     * 일체 변경되지 않고 결과와 메모만 수정되는지 검증한다.
     * (재료 차감과 묶인 값은 되돌리기 어려워 결과와 메모만 수정)
     */
    @Test
    @DisplayName("요리 기록을 수정해도 레시피·상담·회원 값은 그대로이고 결과와 메모만 바뀐다")
    void keepsRecipeAndChatSession_whenCookingLogIsUpdated() {
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
        assertThat(response.result()).isEqualTo(CookingResult.FAILED);
        assertThat(response.memo()).isNull();
        assertThat(response.recipeId()).isEqualTo(recipeId);
        assertThat(response.fromChat()).isTrue();

        // 엔티티 내부 필드 보존 검증
        assertThat(cookingLog.getMemberId()).isEqualTo(memberId);
        assertThat(cookingLog.getRecipeId()).isEqualTo(recipeId);
        assertThat(cookingLog.getChatSessionId()).isEqualTo(chatSessionId);
    }

    /**
     * 레시피 요리 통계 조회 시 repository.getCookingStats(recipeId)를 호출하여 반환하는지 검증한다.
     */
    @Test
    @DisplayName("레시피 요리 통계를 조회하면 저장소의 집계 결과를 반환한다")
    void returnsCookingStats() {
        // Given
        Long recipeId = 12L;
        CookingStatsResponse expected = new CookingStatsResponse(recipeId, 10L, 7L, 3L);
        when(recipeService.existsById(recipeId)).thenReturn(true);
        when(cookingLogRepository.getCookingStats(recipeId)).thenReturn(expected);

        // When
        CookingStatsResponse actual = cookingService.getCookingStats(recipeId);

        // Then
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.recipeId()).isEqualTo(recipeId);
        assertThat(actual.totalCount()).isEqualTo(10L);
        assertThat(actual.successCount()).isEqualTo(7L);
        assertThat(actual.failedCount()).isEqualTo(3L);
        verify(recipeService).existsById(recipeId);
        verify(cookingLogRepository).getCookingStats(recipeId);
    }

    /**
     * 레시피 요리 통계 조회 시 존재하지 않는 레시피이면 404 RECIPE_NOT_FOUND 예외가 발생한다.
     */
    @Test
    @DisplayName("없는 레시피의 요리 통계를 조회하면 RECIPE_NOT_FOUND 예외를 던진다")
    void throwsRecipeNotFound_whenStatsRecipeIsMissing() {
        // Given
        Long recipeId = 999L;
        when(recipeService.existsById(recipeId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> cookingService.getCookingStats(recipeId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPE_NOT_FOUND)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(cookingLogRepository, never()).getCookingStats(any());
    }

    /**
     * 특정 레시피 요리 완료 여부 조회 시 repository.existsByMemberIdAndRecipeId를 호출하여 반환한다.
     */
    @Test
    @DisplayName("레시피 요리 여부를 조회하면 저장소의 결과를 반환한다")
    void returnsHasCooked() {
        // Given
        Long memberId = 1L;
        Long recipeId = 10L;
        when(cookingLogRepository.existsByMemberIdAndRecipeId(memberId, recipeId)).thenReturn(true);

        // When
        boolean actual = cookingService.hasCooked(memberId, recipeId);

        // Then
        assertThat(actual).isTrue();
        verify(cookingLogRepository).existsByMemberIdAndRecipeId(memberId, recipeId);
    }
}

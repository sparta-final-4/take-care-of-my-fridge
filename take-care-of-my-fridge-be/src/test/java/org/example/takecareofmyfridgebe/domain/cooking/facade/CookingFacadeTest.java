package org.example.takecareofmyfridgebe.domain.cooking.facade;

import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.IngredientDeductionResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.UsedIngredientRequest;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CookingFacade 단위 테스트.
 * 요리 완료 시:
 * 1) 상담 세션 소유권 사전 검증 (타인 세션이면 요리 기록 저장 및 재료 차감 없이 즉시 404 실패)
 * 2) 요리 기록 저장 -> 냉장고 재료 차감 -> 상담 세션 완료 순서 검증
 */
@ExtendWith(MockitoExtension.class)
class CookingFacadeTest {

    @Mock
    private CookingService cookingService;

    @Mock
    private FridgeService fridgeService;

    @Mock
    private ChatSessionService chatSessionService;

    @InjectMocks
    private CookingFacade cookingFacade;

    /**
     * chatSessionId가 존재할 때 타인의 세션이거나 없는 세션이면
     * 요리 기록 저장 및 냉장고 차감 로직을 전혀 실행하지 않고 404 CHAT_ROOM_NOT_FOUND로 즉시 종료되는지 검증한다.
     */
    @Test
    void completeCookingFastFailsWhenChatSessionBelongsToAnotherMember() {
        // Given
        Long memberId = 7L;
        Long invalidSessionId = 999L;
        CookingCompleteRequest request = new CookingCompleteRequest(
                12L, CookingResult.SUCCESS, "맛있게 됨", invalidSessionId, List.of());

        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .when(chatSessionService).validateAndGetSession(memberId, invalidSessionId);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingFacade.completeCooking(memberId, request)
        );
        assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());

        verify(cookingService, never()).saveCookingLog(any(), any());
        verify(fridgeService, never()).consumeForCooking(any(), any(), any());
        verify(chatSessionService, never()).completeSessionFromCooking(any(), any());
    }

    /**
     * chatSessionId가 이미 종료(COMPLETED)된 상담 세션이면
     * 요리 기록 저장 및 냉장고 차감 로직을 전혀 실행하지 않고 409 CHAT_ROOM_ALREADY_COMPLETED로 즉시 실패하는지 검증한다.
     */
    @Test
    void completeCookingFastFailsWithConflictWhenChatSessionIsAlreadyCompleted() {
        // Given
        Long memberId = 7L;
        Long completedSessionId = 20L;
        CookingCompleteRequest request = new CookingCompleteRequest(
                12L, CookingResult.SUCCESS, "완료", completedSessionId, List.of());

        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED))
                .when(chatSessionService).validateAndGetSession(memberId, completedSessionId);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingFacade.completeCooking(memberId, request)
        );
        assertEquals(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED, exception.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getErrorCode().getStatus());

        verify(cookingService, never()).saveCookingLog(any(), any());
        verify(fridgeService, never()).consumeForCooking(any(), any(), any());
        verify(chatSessionService, never()).completeSessionFromCooking(any(), any());
    }

    /**
     * 정상적인 요리 완료 요청 시:
     * 1) 세션 검증 -> 2) 기록 저장 -> 3) 재료 차감 -> 4) 세션 완료 순서로 실행되는지 검증한다.
     */
    @Test
    void completeCookingExecutesInOrderAndReturnsResponse() {
        // Given
        Long memberId = 7L;
        Long sessionId = 10L;
        List<UsedIngredientRequest> used = List.of(new UsedIngredientRequest(3L, new BigDecimal("1.00")));
        CookingCompleteRequest request = new CookingCompleteRequest(
                12L, CookingResult.SUCCESS, "성공", sessionId, used);

        CookingLog cookingLog = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(sessionId)
                .result(CookingResult.SUCCESS)
                .memo("성공")
                .build();
        ReflectionTestUtils.setField(cookingLog, "id", 100L);

        when(cookingService.saveCookingLog(memberId, request)).thenReturn(cookingLog);
        when(fridgeService.consumeForCooking(memberId, 100L, used))
                .thenReturn(List.of(new IngredientDeductionResponse(3L, new BigDecimal("1.00"), BigDecimal.ZERO)));

        // When
        CookingCompleteResponse response = cookingFacade.completeCooking(memberId, request);

        // Then
        assertEquals(100L, response.cookingLogId());
        assertEquals(1, response.deductions().size());
        assertEquals(3L, response.deductions().get(0).ingredientId());

        InOrder inOrder = inOrder(chatSessionService, cookingService, fridgeService);
        inOrder.verify(chatSessionService).validateAndGetSession(memberId, sessionId);
        inOrder.verify(cookingService).saveCookingLog(memberId, request);
        inOrder.verify(fridgeService).consumeForCooking(memberId, 100L, used);
        inOrder.verify(chatSessionService).completeSessionFromCooking(memberId, sessionId);
    }
}

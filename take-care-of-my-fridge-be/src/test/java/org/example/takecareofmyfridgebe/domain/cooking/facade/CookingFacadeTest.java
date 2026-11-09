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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CookingFacade 단위 테스트.
 * 요리 완료 시 이을 상담 찾기 -> 요리 기록 저장 -> 냉장고 재료 차감 -> 상담 종료 순서와,
 * 상담을 잇지 못해도(없음·남의 상담·끝난 상담·조회 오류) 요리 완료는 성공하는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class CookingFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long RECIPE_ID = 12L;

    @Mock
    private CookingService cookingService;

    @Mock
    private FridgeService fridgeService;

    @Mock
    private ChatSessionService chatSessionService;

    @InjectMocks
    private CookingFacade cookingFacade;

    @Test
    @DisplayName("이을 상담을 찾으면 상담 찾기, 그 상담 ID로 기록 저장, 재료 차감, 상담 종료 순서로 처리한다")
    void completesCookingInOrder_whenSessionIsFound() {
        List<UsedIngredientRequest> used = List.of(new UsedIngredientRequest(3L, new BigDecimal("1.00")));
        CookingCompleteRequest request = request(CookingResult.SUCCESS, null, used);
        when(chatSessionService.findCookingSessionId(MEMBER_ID, null, RECIPE_ID)).thenReturn(Optional.of(10L));
        when(cookingService.saveCookingLog(MEMBER_ID, request, 10L)).thenReturn(cookingLog(10L));
        when(fridgeService.consumeForCooking(MEMBER_ID, 100L, used))
                .thenReturn(List.of(new IngredientDeductionResponse(3L, new BigDecimal("1.00"), BigDecimal.ZERO)));

        CookingCompleteResponse response = cookingFacade.completeCooking(MEMBER_ID, request);

        assertThat(response.cookingLogId()).isEqualTo(100L);
        assertThat(response.deductions()).extracting(IngredientDeductionResponse::ingredientId).containsExactly(3L);
        InOrder inOrder = inOrder(chatSessionService, cookingService, fridgeService);
        inOrder.verify(chatSessionService).findCookingSessionId(MEMBER_ID, null, RECIPE_ID);
        inOrder.verify(cookingService).saveCookingLog(MEMBER_ID, request, 10L);
        inOrder.verify(fridgeService).consumeForCooking(MEMBER_ID, 100L, used);
        inOrder.verify(chatSessionService).completeSessionFromCooking(MEMBER_ID, 10L);
    }

    @Test
    @DisplayName("이을 상담이 없으면(추천 안 한 레시피·남의 상담·끝난 상담) 상담 없이 기록하고 상담을 끝내지 않는다")
    void savesWithoutSession_whenNoSessionIsFound() {
        CookingCompleteRequest request = request(CookingResult.SUCCESS, 999L, List.of());
        when(chatSessionService.findCookingSessionId(MEMBER_ID, 999L, RECIPE_ID)).thenReturn(Optional.empty());
        when(cookingService.saveCookingLog(MEMBER_ID, request, null)).thenReturn(cookingLog(null));

        CookingCompleteResponse response = cookingFacade.completeCooking(MEMBER_ID, request);

        assertThat(response.cookingLogId()).isEqualTo(100L);
        verify(fridgeService).consumeForCooking(MEMBER_ID, 100L, List.of());
        verify(chatSessionService, never()).completeSessionFromCooking(any(), any());
    }

    @Test
    @DisplayName("상담을 찾다가 오류가 나도 상담 없이 요리 완료를 마친다")
    void completesCookingWithoutSession_whenLookupFails() {
        CookingCompleteRequest request = request(CookingResult.SUCCESS, null, List.of());
        when(chatSessionService.findCookingSessionId(MEMBER_ID, null, RECIPE_ID))
                .thenThrow(new IllegalStateException("DB 오류"));
        when(cookingService.saveCookingLog(eq(MEMBER_ID), eq(request), isNull())).thenReturn(cookingLog(null));

        CookingCompleteResponse response = cookingFacade.completeCooking(MEMBER_ID, request);

        assertThat(response.cookingLogId()).isEqualTo(100L);
        verify(chatSessionService, never()).completeSessionFromCooking(any(), any());
    }

    @Test
    @DisplayName("요리 결과가 실패여도 이은 상담을 끝낸다")
    void completesSession_whenResultIsFailed() {
        CookingCompleteRequest request = request(CookingResult.FAILED, null, List.of());
        when(chatSessionService.findCookingSessionId(MEMBER_ID, null, RECIPE_ID)).thenReturn(Optional.of(10L));
        when(cookingService.saveCookingLog(MEMBER_ID, request, 10L)).thenReturn(cookingLog(10L));

        cookingFacade.completeCooking(MEMBER_ID, request);

        verify(chatSessionService).completeSessionFromCooking(MEMBER_ID, 10L);
    }

    private static CookingCompleteRequest request(CookingResult result, Long chatSessionId, List<UsedIngredientRequest> used) {
        return new CookingCompleteRequest(RECIPE_ID, result, "메모", chatSessionId, used);
    }

    private static CookingLog cookingLog(Long chatSessionId) {
        CookingLog cookingLog = CookingLog.builder()
                .memberId(MEMBER_ID)
                .recipeId(RECIPE_ID)
                .chatSessionId(chatSessionId)
                .result(CookingResult.SUCCESS)
                .memo("메모")
                .build();
        ReflectionTestUtils.setField(cookingLog, "id", 100L);
        return cookingLog;
    }
}

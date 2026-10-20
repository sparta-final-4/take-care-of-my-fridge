package org.example.takecareofmyfridgebe.domain.cooking.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.IngredientDeductionResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CookingFacade {

    private final CookingService cookingService;
    private final FridgeService fridgeService;
    private final ChatSessionService chatSessionService;

    /**
     * 요리 완료를 하나의 트랜잭션으로 처리한다:
     * 1. chatSessionId가 있으면 내 상담인지 먼저 확인 (아니면 404 즉시 실패, 불필요한 락/저장 방지)
     * 2. 요리 기록(CookingLog) 저장
     * 3. 냉장고 재료 차감 (비관적 락, 소비기한 빠른 순 순차 차감, FridgeItemLog 생성, 잔여량 0 시 삭제)
     * 4. 상담 세션이 ACTIVE 상태이면 종료(complete) 처리
     *
     * @param memberId 회원 ID
     * @param request  요리 완료 요청 정보
     * @return 요리 완료 응답 (기록 ID 및 재료별 차감/잔여량)
     */
    @Transactional
    public CookingCompleteResponse completeCooking(Long memberId, CookingCompleteRequest request) {
        // 1. 상담 세션 사전 검증
        if (request.chatSessionId() != null) {
            chatSessionService.validateAndGetSession(memberId, request.chatSessionId());
        }

        // 2. 요리 기록 저장
        CookingLog cookingLog = cookingService.saveCookingLog(memberId, request);

        // 3. 냉장고 재료 차감
        List<IngredientDeductionResponse> deductions = fridgeService.consumeForCooking(
                memberId,
                cookingLog.getId(),
                request.usedIngredients()
        );

        // 4. 상담 세션 종료 처리 (ACTIVE 상태일 때만 종료, 이미 만료/종료된 상태는 건너뜀)
        if (request.chatSessionId() != null) {
            chatSessionService.completeSessionFromCooking(memberId, request.chatSessionId());
        }

        return new CookingCompleteResponse(cookingLog.getId(), deductions);
    }
}

package org.example.takecareofmyfridgebe.domain.cooking.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.IngredientDeductionResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.event.CookingCompletedEvent;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookingFacade {

    private final CookingService cookingService;
    private final FridgeService fridgeService;
    private final ChatSessionService chatSessionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 요리 완료를 하나의 트랜잭션으로 처리하고, 커밋된 뒤 이은 상담을 끝낸다:
     * 1. 이을 상담을 찾는다 (읽기만, 찾지 못하거나 오류가 나면 잇지 않고 요리 완료는 그대로 진행)
     *    - 요청에 chatSessionId가 있으면 내 진행 중 상담일 때만, 없으면 진행 중 상담에서 이 레시피를 추천한 적 있을 때
     * 2. 요리 기록(CookingLog) 저장 (이은 상담 ID 포함)
     * 3. 냉장고 재료 차감 (비관적 락, 소비기한 빠른 순 순차 차감, FridgeItemLog 생성, 잔여량 0 시 삭제)
     * 4. 이은 상담이 있으면 요리 완료 이벤트를 발행한다. 커밋 뒤 CookingCompletedEventListener가 상담을 끝낸다
     *    (요리 결과가 실패여도 끝내고, 상담 종료가 실패해도 요리 완료는 되돌리지 않는다)
     *
     * @param memberId 회원 ID
     * @param request  요리 완료 요청 정보
     * @return 요리 완료 응답 (기록 ID 및 재료별 차감/잔여량)
     */
    @Transactional
    public CookingCompleteResponse completeCooking(Long memberId, CookingCompleteRequest request) {
        // 1. 이을 상담 찾기
        Long chatSessionId = findChatSessionId(memberId, request);

        // 2. 요리 기록 저장
        CookingLog cookingLog = cookingService.saveCookingLog(memberId, request, chatSessionId);

        // 3. 냉장고 재료 차감
        List<IngredientDeductionResponse> deductions = fridgeService.consumeForCooking(
                memberId,
                cookingLog.getId(),
                request.usedIngredients()
        );

        // 4. 상담 종료는 커밋 뒤 (진행 중일 때만 끝내고, 이미 끝난 상담은 건너뜀)
        if (chatSessionId != null) {
            eventPublisher.publishEvent(new CookingCompletedEvent(memberId, chatSessionId, cookingLog.getId()));
        }

        return new CookingCompleteResponse(cookingLog.getId(), deductions);
    }

    // 상담 연결은 덤이라, 찾다가 오류가 나도 요리 완료를 막지 않는다
    private Long findChatSessionId(Long memberId, CookingCompleteRequest request) {
        try {
            return chatSessionService.findCookingSessionId(memberId, request.chatSessionId(), request.recipeId())
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("요리 완료 상담 연결 조회 실패, 잇지 않고 진행: memberId={}, recipeId={}",
                    memberId, request.recipeId(), e);
            return null;
        }
    }
}

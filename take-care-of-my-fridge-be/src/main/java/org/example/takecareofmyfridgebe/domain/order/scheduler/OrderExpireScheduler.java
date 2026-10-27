package org.example.takecareofmyfridgebe.domain.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.order.service.OrderCancelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 결제하기를 누르지 않고 떠난 결제 대기 주문을 생성 5분 뒤 지운다. 재고는 취소하기와 같은 메서드로 되돌린다.
// 결제를 시도한 주문은 건너뛰고 결제 쪽이 정리한다
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {

    static final int BATCH_SIZE = 100;
    static final long EXPIRE_MINUTES = 5;

    private final OrderCancelService orderCancelService;

    // 이전 실행이 끝나고 1분 뒤 다시 실행해 실행끼리 겹치지 않는다. 기동 직후에는 1분 기다린다
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireAbandonedOrders() {
        LocalDateTime createdBefore = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);
        long afterId = 0;
        List<Long> batch;
        do {
            batch = orderCancelService.findExpiredOrderIds(createdBefore, afterId, BATCH_SIZE);
            for (Long orderId : batch) {
                expire(orderId);
                afterId = orderId;
            }
        } while (batch.size() == BATCH_SIZE);
    }

    // 한 건이 실패해도 나머지는 계속 처리한다. 실패한 건은 결제 대기로 남아 다음 실행에서 다시 시도한다
    private void expire(Long orderId) {
        try {
            orderCancelService.expire(orderId);
        } catch (RuntimeException e) {
            log.error("주문 만료 처리 실패, 다음 실행에서 재시도: orderId={}", orderId, e);
        }
    }
}

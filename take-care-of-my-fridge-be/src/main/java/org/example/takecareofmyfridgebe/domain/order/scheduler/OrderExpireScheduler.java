package org.example.takecareofmyfridgebe.domain.order.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.order.facade.OrderFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 결제하기를 누르지 않고 떠난 결제 대기 주문을 생성 5분 뒤 지운다. 재고는 취소하기와 같은 메서드로 되돌린다.
// 결제를 시도한 주문은 건너뛰고 결제 쪽이 정리한다
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {

    static final long EXPIRE_MINUTES = 5;

    private final OrderFacade orderFacade;

    // 이전 실행이 끝나고 1분 뒤 다시 실행해 실행끼리 겹치지 않는다. 기동 직후에는 1분 기다린다
    // 서버가 여러 대여도 한 서버만 실행한다. 주문 행 잠금 덕에 함께 돌아도 결과는 같고, 같은 주문을 두 번 처리하는 일만 막는다
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @SchedulerLock(name = "orderExpire", lockAtMostFor = "30m")
    public void expireAbandonedOrders() {
        orderFacade.expireOrders(LocalDateTime.now().minusMinutes(EXPIRE_MINUTES));
    }
}

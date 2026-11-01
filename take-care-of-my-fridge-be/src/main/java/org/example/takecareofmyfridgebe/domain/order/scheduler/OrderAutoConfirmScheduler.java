package org.example.takecareofmyfridgebe.domain.order.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.facade.OrderFacade;
import org.example.takecareofmyfridgebe.domain.order.service.OrderStatusService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 결제 후 7일이 지난 주문을 구매 확정한다. 환불이 진행 중이거나 전부 환불된 주문은 확정하지 않고 건너뛴다.
// 주문마다 트랜잭션이 따로 열려 한 건이 실패해도 나머지는 계속 처리한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoConfirmScheduler {

    static final int BATCH_SIZE = 100;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderStatusService orderStatusService;
    private final OrderFacade orderFacade;

    // 서버가 여러 대여도 한 서버만 실행한다. 주문 행 잠금 덕에 함께 돌아도 결과는 같고, 같은 주문을 두 번 처리하는 일만 막는다
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "orderAutoConfirm", lockAtMostFor = "30m")
    public void confirmDueOrders() {
        confirmDueOrders(LocalDateTime.now(ZONE));
    }

    void confirmDueOrders(LocalDateTime now) {
        LocalDateTime paidBefore = now.minusDays(7);
        long afterId = 0;
        List<Order> batch;
        do {
            batch = orderStatusService.findPaidOrders(paidBefore, afterId, BATCH_SIZE);
            for (Order order : batch) {
                afterId = order.getId();
                confirm(order);
            }
        } while (batch.size() == BATCH_SIZE);
    }

    private void confirm(Order order) {
        try {
            orderFacade.confirm(order.getMemberId(), order.getId());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.ORDER_REFUND_IN_PROGRESS) {
                log.info("환불 요청 중인 주문 자동 확정 보류: orderId={}", order.getId());
            } else if (e.getErrorCode() == ErrorCode.ORDER_FULLY_REFUNDED) {
                // 결제 완료로 남아 다음 실행에서도 다시 조회되지만 확정하지 않는다
                log.info("전부 환불된 주문 자동 확정 제외: orderId={}", order.getId());
            } else {
                log.error("주문 자동 확정 실패, 다음 실행에서 재시도: orderId={}", order.getId(), e);
            }
        } catch (RuntimeException e) {
            log.error("주문 자동 확정 실패, 다음 실행에서 재시도: orderId={}", order.getId(), e);
        }
    }
}

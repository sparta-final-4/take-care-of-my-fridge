package org.example.takecareofmyfridgebe.domain.order.facade;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.service.OrderStatusService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

// 스케줄러가 주문을 여러 건 이어서 처리할 때 건마다 상태 서비스를 따로 부르고, 한 건이 실패해도 나머지를 계속 처리한다.
// 건마다 트랜잭션이 따로 열리도록 이 클래스는 트랜잭션을 들지 않는다.
// 주문하기·취소·구매 확정 같은 한 건 처리는 합칠 것이 없어 컨트롤러가 서비스를 바로 부른다
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    static final int BATCH_SIZE = 100;

    private final OrderStatusService orderStatusService;

    // 결제 후 일정 기간이 지난 주문을 구매 확정한다. 환불이 진행 중이거나 전부 환불된 주문은 확정하지 않고 건너뛴다.
    public void confirmDueOrders(LocalDateTime paidBefore) {
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

    // 결제를 누르지 않고 떠난 결제 대기 주문을 지우고 재고를 되돌린다. 결제가 붙은 주문은 건너뛰고 결제 쪽이 정리한다.
    public void expireOrders(LocalDateTime createdBefore) {
        long afterId = 0;
        List<Long> batch;
        do {
            batch = orderStatusService.findExpiredOrderIds(createdBefore, afterId, BATCH_SIZE);
            for (Long orderId : batch) {
                expire(orderId);
                afterId = orderId;
            }
        } while (batch.size() == BATCH_SIZE);
    }

    private void confirm(Order order) {
        try {
            orderStatusService.confirm(order.getMemberId(), order.getId());
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

    // 한 건이 실패해도 나머지는 계속 처리한다. 실패한 건은 결제 대기로 남아 다음 실행에서 다시 시도한다
    private void expire(Long orderId) {
        try {
            orderStatusService.expire(orderId);
        } catch (RuntimeException e) {
            log.error("주문 만료 처리 실패, 다음 실행에서 재시도: orderId={}", orderId, e);
        }
    }
}

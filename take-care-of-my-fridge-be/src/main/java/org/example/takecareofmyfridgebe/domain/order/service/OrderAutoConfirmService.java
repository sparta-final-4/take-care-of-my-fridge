package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutoConfirmService {

    static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderConfirmService confirmService;

    public void confirmDue(LocalDateTime paidBefore) {
        long afterId = 0;
        List<Order> batch;
        do {
            batch = orderRepository.findPaidBefore(OrderStatus.PAID, paidBefore, afterId,
                    PageRequest.of(0, BATCH_SIZE));
            for (Order order : batch) {
                afterId = order.getId();
                try {
                    confirmService.confirm(order.getMemberId(), order.getId());
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ORDER_REFUND_IN_PROGRESS) {
                        log.info("환불 요청 중인 주문 자동 확정 보류: orderId={}", order.getId());
                    } else {
                        log.error("주문 자동 확정 실패, 다음 실행에서 재시도: orderId={}", order.getId(), e);
                    }
                } catch (RuntimeException e) {
                    log.error("주문 자동 확정 실패, 다음 실행에서 재시도: orderId={}", order.getId(), e);
                }
            }
        } while (batch.size() == BATCH_SIZE);
    }
}

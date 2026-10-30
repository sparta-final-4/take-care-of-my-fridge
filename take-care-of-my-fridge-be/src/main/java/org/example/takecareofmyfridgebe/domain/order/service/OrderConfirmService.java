package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.event.OrderConfirmedEvent;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 결제 완료 주문을 구매 확정으로 바꾼다. 구매 확정 뒤에는 환불할 수 없다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderConfirmService {

    private final OrderReader orderReader;
    private final RefundQueryService refundQueryService;
    private final ApplicationEventPublisher eventPublisher;

    // 주문 행을 잠그고 상태를 확인하므로 같은 주문의 구매 확정이 동시에 와도 하나씩 순서대로 처리된다.
    // 결제 완료에서 구매 확정으로 바꾼 요청만 확정 시각을 남기고, 뒤에 온 요청은 이미 확정된 주문을 보고 같은 응답만 돌려준다.
    // 환불 신청도 같은 주문 행을 잠그므로, 잠근 뒤 요청 상태 환불이 없으면 확정이 끝날 때까지 새 환불이 생기지 않는다.
    @Transactional
    public OrderConfirmResponse confirm(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        if (order.isConfirmed()) {
            return OrderConfirmResponse.from(order);
        }
        if (refundQueryService.hasRequestedRefund(order.getId())) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_IN_PROGRESS);
        }
        order.confirm(LocalDateTime.now());
        List<OrderItem> items = orderReader.getOrderItems(order);
        confirmQuantities(items);
        // 이미 확정된 주문은 위에서 돌려보내므로 처음 확정한 요청만 여기서 발행한다. 실제 발행은 커밋 뒤에 한다.
        // 상품이 모두 환불된 주문도 확정마다 한 번 발행하며, 이때 실린 상품 목록은 비어 있다
        eventPublisher.publishEvent(OrderConfirmedEvent.of(order, items));
        return OrderConfirmResponse.from(order);
    }

    // 확정 수량은 주문 수량에서 완료된 환불 수량을 뺀 값이다. 할인 상품은 환불이 없어 주문 수량과 같다
    private void confirmQuantities(List<OrderItem> items) {
        Map<Long, Long> completedById = refundQueryService.getCompletedRefundQuantities(
            items.stream().map(OrderItem::getId).toList());
        items.forEach(item -> item.confirmQuantity(completedById.getOrDefault(item.getId(), 0L)));
    }
}

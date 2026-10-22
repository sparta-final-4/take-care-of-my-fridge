package org.example.takecareofmyfridgebe.domain.order.dto;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;

// 내 주문 내역 화면의 주문 한 건. 주문 상품은 담지 않고, 상세 보기에서 따로 읽는다.
public record OrderSummaryResponse(
        Long orderId,
        String orderNumber,
        LocalDateTime orderedAt,
        long payAmount,
        OrderStatus status
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getTotalAmount(),
                order.getStatus()
        );
    }
}

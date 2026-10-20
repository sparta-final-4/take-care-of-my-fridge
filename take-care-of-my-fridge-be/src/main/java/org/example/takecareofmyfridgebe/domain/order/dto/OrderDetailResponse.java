package org.example.takecareofmyfridgebe.domain.order.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;

public record OrderDetailResponse(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        LocalDateTime orderedAt,
        long listPriceAmount,
        long discountAmount,
        long payAmount,
        List<OrderItemResponse> items
) {

    public static OrderDetailResponse from(Order order, List<OrderItem> items) {
        long listPriceAmount = items.stream()
                .mapToLong(item -> (long) item.getListPrice() * item.getQuantity())
                .sum();
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                listPriceAmount,
                // totalAmount는 할인 후 실제 결제 금액이다. 할인 연동 뒤에도 이 의미를 유지해야 할인 금액이 맞다.
                listPriceAmount - order.getTotalAmount(),
                order.getTotalAmount(),
                items.stream().map(OrderItemResponse::from).toList()
        );
    }
}

package org.example.takecareofmyfridgebe.domain.order.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;

// 주문 상세 화면을 그리는 응답. 환불 내역은 환불 상세 화면이 따로 보여 주므로 담지 않는다.
public record OrderDetailResponse(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        LocalDateTime orderedAt,
        LocalDateTime confirmedAt,
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
                order.getConfirmedAt(),
                listPriceAmount,
                listPriceAmount - order.getTotalAmount(),
                order.getTotalAmount(),
                items.stream().map(OrderItemResponse::from).toList()
        );
    }
}

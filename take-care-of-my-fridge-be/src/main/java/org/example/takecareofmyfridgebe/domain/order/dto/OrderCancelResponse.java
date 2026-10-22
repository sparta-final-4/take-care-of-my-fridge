package org.example.takecareofmyfridgebe.domain.order.dto;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;

public record OrderCancelResponse(Long orderId, OrderStatus status) {

    public static OrderCancelResponse from(Order order) {
        return new OrderCancelResponse(order.getId(), order.getStatus());
    }
}

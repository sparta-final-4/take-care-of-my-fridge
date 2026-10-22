package org.example.takecareofmyfridgebe.domain.order.dto;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;

public record OrderConfirmResponse(Long orderId, OrderStatus status) {

    public static OrderConfirmResponse from(Order order) {
        return new OrderConfirmResponse(order.getId(), order.getStatus());
    }
}

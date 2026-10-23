package org.example.takecareofmyfridgebe.domain.refund.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

// 환불 신청과 환불 가능 수량 조회가 같은 규칙을 쓰도록 모은다
final class RefundableOrderPolicy {

    private RefundableOrderPolicy() {
    }

    static void validateRefundableOrder(Order order) {
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REFUND_ORDER_CONFIRMED);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS);
        }
    }

    // 특가·번개 드롭 상품은 환불할 수 없다
    static boolean isRefundableItem(OrderItem orderItem) {
        return orderItem.getSaleType() == SaleType.NONE;
    }
}

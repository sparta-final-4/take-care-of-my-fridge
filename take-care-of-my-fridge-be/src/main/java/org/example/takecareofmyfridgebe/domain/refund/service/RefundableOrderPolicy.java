package org.example.takecareofmyfridgebe.domain.refund.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// 환불 신청, 환불 가능 수량 조회, 환불 완료 뒤 주문 상태 판단이 같은 규칙을 쓰도록 모은다
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

    // 환불 완료 뒤 주문이 옮겨 갈 상태. 환불할 수 있는 상품이 모두 환불됐을 때만 상태가 바뀐다.
    // 특가·번개 드롭 상품이 없으면 전액 환불, 있으면 남은 상품을 바로 구매 확정한다.
    // 부분 환불이거나 환불할 수 있는 상품이 하나도 없는 주문이면 비어 있다
    static Optional<OrderStatus> statusAfterRefund(List<OrderItem> orderItems, Map<Long, Long> completedById) {
        List<OrderItem> refundableItems = orderItems.stream().filter(RefundableOrderPolicy::isRefundableItem).toList();
        boolean allRefunded = !refundableItems.isEmpty() && refundableItems.stream()
                .allMatch(item -> completedById.getOrDefault(item.getId(), 0L) >= item.getQuantity());
        if (!allRefunded) {
            return Optional.empty();
        }
        boolean hasSaleItem = orderItems.stream().anyMatch(item -> !isRefundableItem(item));
        return Optional.of(hasSaleItem ? OrderStatus.CONFIRMED : OrderStatus.REFUNDED);
    }
}

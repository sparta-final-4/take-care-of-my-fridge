package org.example.takecareofmyfridgebe.domain.order.event;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;

// 구매 확정 이벤트. 냉장고 입고에 쓰도록 확정 수량이 남은 주문 상품만 싣는다
public record OrderConfirmedEvent(
        Long orderId,
        Long memberId,
        LocalDateTime confirmedAt,
        List<Item> items
) {

    public static OrderConfirmedEvent of(Order order, List<OrderItem> orderItems) {
        List<Item> items = orderItems.stream()
                .filter(item -> item.getConfirmedQuantity() > 0)
                .map(Item::from)
                .toList();
        return new OrderConfirmedEvent(order.getId(), order.getMemberId(), order.getConfirmedAt(), items);
    }

    public record Item(
            Long orderItemId,
            Long productId,
            String productName,
            int confirmedQuantity
    ) {

        static Item from(OrderItem item) {
            return new Item(item.getId(), item.getProductId(), item.getProductName(), item.getConfirmedQuantity());
        }
    }
}

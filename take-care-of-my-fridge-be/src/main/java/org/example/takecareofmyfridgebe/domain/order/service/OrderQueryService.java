package org.example.takecareofmyfridgebe.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderReader orderReader;

    // 주문 확인 화면(결제 대기 주문)용. 결제 이후 주문은 주문 상세 화면이 따로 보여 준다.
    public OrderCheckoutResponse getCheckout(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrder(memberId, orderId);
        order.validatePending();
        return OrderCheckoutResponse.from(order, orderReader.getOrderItems(order));
    }
}

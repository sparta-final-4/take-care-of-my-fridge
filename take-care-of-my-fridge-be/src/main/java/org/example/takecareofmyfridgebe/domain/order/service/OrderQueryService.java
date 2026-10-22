package org.example.takecareofmyfridgebe.domain.order.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderListRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderSummaryResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    // 결제 대기는 PG 결제가 끝났는데 서버 확인이 늦어진 주문일 수 있어 보인다. 취소는 결제가 끝나지 않은 주문이라 뺀다.
    private static final List<OrderStatus> LISTED_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CONFIRMED);

    private final OrderReader orderReader;
    private final OrderRepository orderRepository;

    // 주문 확인 화면(결제 대기 주문)용. 결제 이후 주문은 주문 상세 화면이 따로 보여 준다.
    public OrderCheckoutResponse getCheckout(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrder(memberId, orderId);
        order.validatePending();
        return OrderCheckoutResponse.from(order, orderReader.getOrderItems(order));
    }

    // 내 주문 내역 화면용. 최신 주문이 먼저 온다. id는 주문 순서대로 늘어나 같은 시각의 주문도 순서가 정해진다.
    public PageResponse<OrderSummaryResponse> getMyOrders(Long memberId, OrderListRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size(), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(orderRepository.findByMemberIdAndStatusIn(memberId, LISTED_STATUSES, pageable)
                .map(OrderSummaryResponse::from));
    }
}

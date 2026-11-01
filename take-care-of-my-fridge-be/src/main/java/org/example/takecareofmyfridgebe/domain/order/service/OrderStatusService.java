package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.event.OrderConfirmedEvent;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 주문 상태를 바꾼다. 구매 확정, 결제를 시도한 주문의 취소, 결제 전 주문의 삭제.
// 주문 행 잠금, 결제·환불 확인, 재고 되돌리기는 OrderFacade가 하고, 이 서비스는 그 트랜잭션 안에서 불린다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatusService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 주문 만료 스케줄러가 처리할 결제 대기 주문을 afterId 다음부터 id 순으로 조회한다.
    // 결제가 붙었는지는 여기서 거르지 않고 만료할 때 주문 행을 잠근 뒤 본다.
    public List<Long> findExpiredOrderIds(LocalDateTime createdBefore, long afterId, int limit) {
        return orderRepository.findIdsCreatedBefore(OrderStatus.PENDING, createdBefore, afterId, PageRequest.of(0, limit));
    }

    // 자동 확정 스케줄러가 처리할 결제 완료 주문을 afterId 다음부터 id 순으로 조회한다.
    public List<Order> findPaidOrders(LocalDateTime paidBefore, long afterId, int limit) {
        return orderRepository.findPaidBefore(OrderStatus.PAID, paidBefore, afterId, PageRequest.of(0, limit));
    }

    // 결제를 시도했지만 끝내지 않은 주문을 취소 상태로 남긴다. 결제 행이 주문을 참조해 지우지 않는다.
    // 1인 1개 기록을 풀어 같은 번개 드롭을 다시 살 수 있게 한다.
    @Transactional
    public void cancel(Order order, List<OrderItem> items) {
        order.cancel();
        items.forEach(OrderItem::releaseFlashDropMember);
    }

    // 확정 수량은 주문 수량에서 완료된 환불 수량을 뺀 값이다. 할인 상품은 환불이 없어 주문 수량과 같다.
    // 이미 확정된 주문은 퍼사드가 먼저 돌려보내므로 처음 확정한 요청만 이벤트를 발행한다. 실제 발행은 커밋 뒤에 한다.
    @Transactional
    public OrderConfirmResponse confirm(Order order, List<OrderItem> items, Map<Long, Long> completedRefundQuantities) {
        order.confirm(LocalDateTime.now());
        items.forEach(item -> item.confirmQuantity(completedRefundQuantities.getOrDefault(item.getId(), 0L)));
        eventPublisher.publishEvent(OrderConfirmedEvent.of(order, items));
        return OrderConfirmResponse.from(order);
    }

    // 결제를 시도하지 않은 결제 대기 주문을 지운다. 1인 1개 기록도 주문 상품 행과 함께 사라진다.
    @Transactional
    public void delete(Order order, List<OrderItem> items) {
        orderItemRepository.deleteAll(items);
        orderRepository.delete(order);
    }
}

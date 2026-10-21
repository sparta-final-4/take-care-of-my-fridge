package org.example.takecareofmyfridgebe.domain.order.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 여러 주문 기능이 같이 쓰는 조회와 소유 확인을 모은다. 상태 규칙은 Order 엔티티에 둔다.
// 부르는 서비스에 이미 트랜잭션이 있으면 새로 열지 않고 그 트랜잭션 안에서 실행된다. 그래서 쓰기 트랜잭션 안에서 불러도 잠금이 유지된다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReader {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // 남의 주문도 없는 주문과 똑같이 404로 답해, 주문 번호가 있는지 드러내지 않는다
    public Order getOwnedOrder(Long memberId, Long orderId) {
        return orderRepository.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    // 상태를 바꾸기 전에 주문 행을 잠그고 찾는다. 잠금은 부르는 서비스의 트랜잭션이 끝날 때 풀린다.
    public Order getOwnedOrderForUpdate(Long memberId, Long orderId) {
        return orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public List<OrderItem> getOrderItems(Order order) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
    }
}

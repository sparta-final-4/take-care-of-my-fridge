package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 결제 완료 주문을 구매 확정으로 바꾼다. 구매 확정 뒤에는 환불할 수 없다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderConfirmService {

    private final OrderReader orderReader;

    // 주문 행을 잠그고 상태를 확인하므로 같은 주문의 구매 확정이 동시에 와도 하나씩 순서대로 처리된다.
    // 결제 완료에서 구매 확정으로 바꾼 요청만 확정 시각을 남기고, 뒤에 온 요청은 이미 확정된 주문을 보고 같은 응답만 돌려준다.
    @Transactional
    public OrderConfirmResponse confirm(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        if (order.isConfirmed()) {
            return OrderConfirmResponse.from(order);
        }
        order.confirm(LocalDateTime.now());
        return OrderConfirmResponse.from(order);
    }
}

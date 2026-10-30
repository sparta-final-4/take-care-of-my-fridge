package org.example.takecareofmyfridgebe.domain.payment.event;

// 결제 승인으로 주문이 결제 완료가 됐을 때 발행한다. 장바구니가 결제한 상품을 지우는 데 쓴다
public record PaymentCompletedEvent(
        Long orderId,
        Long memberId
) {
}

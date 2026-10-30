package org.example.takecareofmyfridgebe.domain.cart.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.payment.event.PaymentCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 결제 완료가 커밋된 뒤 결제한 상품을 장바구니에서 지운다. 결제는 이미 끝났으므로 지우지 못해도 결제를 되돌리지 않고 주문 ID를 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedCartListener {

    private final CartService cartService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            cartService.removePaidItems(event.memberId(), event.orderId());
        } catch (RuntimeException e) {
            log.error("결제한 상품 장바구니 삭제 실패: orderId={}", event.orderId(), e);
        }
    }
}

package org.example.takecareofmyfridgebe.domain.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 구매 확정이 커밋된 뒤 Kafka로 발행한다. 확정은 이미 끝났으므로 발행이 실패해도 확정을 되돌리지 않고 주문 ID를 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.PURCHASE_CONFIRMED, String.valueOf(event.orderId()), event)
                    .whenComplete((result, e) -> {
                        if (e != null) {
                            log.error("구매 확정 이벤트 발행 실패: orderId={}", event.orderId(), e);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("구매 확정 이벤트 발행 실패: orderId={}", event.orderId(), e);
        }
    }
}

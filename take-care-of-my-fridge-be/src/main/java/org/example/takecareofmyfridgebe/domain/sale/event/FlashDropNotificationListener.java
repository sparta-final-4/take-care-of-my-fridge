package org.example.takecareofmyfridgebe.domain.sale.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
import org.example.takecareofmyfridgebe.domain.sale.service.FlashDropNotificationReader;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 번개 드롭 등록·시작이 커밋된 뒤 받는 사람마다 알림 요청을 발행한다.
// 행사는 이미 저장됐으므로 발행이 실패해도 되돌리지 않고 행사 ID를 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashDropNotificationListener {

    private final FlashDropNotificationReader flashDropNotificationReader;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlashDropNotification(FlashDropNotificationEvent event) {
        try {
            List<NotificationRequestedMessage> messages = flashDropNotificationReader.read(event);
            messages.forEach(message -> send(event, message));
        } catch (RuntimeException e) {
            log.error("번개 드롭 알림 요청 발행 실패: moment={}, saleEventIds={}", event.moment(), event.saleEventIds(), e);
        }
    }

    private void send(FlashDropNotificationEvent event, NotificationRequestedMessage message) {
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_REQUESTED, String.valueOf(message.memberId()), message)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        log.error("번개 드롭 알림 요청 발행 실패: moment={}, dedupeKey={}, memberId={}",
                                event.moment(), message.dedupeKey(), message.memberId(), e);
                    }
                });
    }
}

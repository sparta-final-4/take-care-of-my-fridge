package org.example.takecareofmyfridgebe.domain.notification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.notification.push.NotificationPushPublisher;
import org.example.takecareofmyfridgebe.domain.notification.service.NotificationCreateService;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 알림 요청을 받아 알림으로 저장한다.
// 같은 회원에게 같은 dedupeKey가 다시 오면 유니크 제약 위반으로 알아채고 건너뛴다.
// 위반은 저장 트랜잭션이 롤백된 뒤 여기서 잡는다. 트랜잭션 안에서 잡으면 롤백 표시가 남아 커밋 때 다시 실패한다.
// 그 밖의 실패는 다시 던져 재시도 뒤 DLT로 보낸다
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequestedConsumer {

    private static final String DEDUPE_KEY_UNIQUE = "uk_notifications_member_id_dedupe_key";

    private final NotificationCreateService notificationCreateService;
    private final NotificationPushPublisher notificationPushPublisher;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_REQUESTED, groupId = "notification")
    public void consume(NotificationRequestedMessage message) {
        try {
            notificationCreateService.create(message);
            notificationPushPublisher.publish(message.memberId());
        } catch (DataIntegrityViolationException e) {
            if (!constraintName(e).contains(DEDUPE_KEY_UNIQUE)) {
                throw e;
            }
            log.info("이미 저장된 알림 요청 건너뜀: memberId={}, dedupeKey={}", message.memberId(), message.dedupeKey());
        }
    }

    private String constraintName(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName();
        }
        return "";
    }
}

package org.example.takecareofmyfridgebe.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.push.NotificationPushPublisher;
import org.example.takecareofmyfridgebe.domain.notification.service.NotificationCreateService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// 저장 결과에 따라 실시간 신호를 내보낼지만 본다. 저장 자체는 NotificationRequestedConsumerTest가 DB로 확인한다
class NotificationRequestedConsumerPushTest {

    private final NotificationCreateService createService = mock(NotificationCreateService.class);
    private final NotificationPushPublisher publisher = mock(NotificationPushPublisher.class);
    private final NotificationRequestedConsumer consumer = new NotificationRequestedConsumer(createService, publisher);
    private final NotificationRequestedMessage message = new NotificationRequestedMessage(
            7L, NotificationType.FLASH_DROP, "번개 드롭 시작", "/products/1", "FLASH_DROP_STARTED:1:1");

    @Test
    @DisplayName("알림을 새로 저장했으면 그 회원에게 실시간 신호를 한 번 내보낸다")
    void consume_saved_publishesOnce() {
        consumer.consume(message);

        verify(createService).create(message);
        verify(publisher).publish(7L);
    }

    @Test
    @DisplayName("같은 중복 방지 키라 건너뛰면 오류 없이 끝나고 신호는 내보내지 않는다")
    void consume_duplicate_skipsWithoutPublishing() {
        willThrow(violation("uk_notifications_member_id_dedupe_key")).given(createService).create(message);

        assertThatCode(() -> consumer.consume(message)).doesNotThrowAnyException();

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("중복 키가 아닌 제약 위반은 다시 던져 재시도·DLT로 보내고 신호는 내보내지 않는다")
    void consume_otherViolation_rethrowsWithoutPublishing() {
        willThrow(violation("some_other_constraint")).given(createService).create(message);

        assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(DataIntegrityViolationException.class);

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("저장이 다른 이유로 실패하면 다시 던지고 신호는 내보내지 않는다")
    void consume_saveFailure_rethrowsWithoutPublishing() {
        willThrow(new IllegalStateException("db down")).given(createService).create(message);

        assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(IllegalStateException.class);

        verify(publisher, never()).publish(any());
    }

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("저장 실패",
                new ConstraintViolationException("제약 위반", new SQLException("dup"), constraintName));
    }
}

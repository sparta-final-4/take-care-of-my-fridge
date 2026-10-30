package org.example.takecareofmyfridgebe.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.repository.NotificationRepository;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.example.takecareofmyfridgebe.support.KafkaContainerSupport;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 실제 토픽에 보낸 알림 요청이 알림 테이블에 어떻게 남는지 DB를 다시 읽어 확인한다.
// 컨테이너는 다른 테스트와 공유하므로 테스트마다 다른 회원 ID를 쓴다
@SpringBootTest
@Import(NotificationRequestedConsumerTest.DltConsumerConfig.class)
class NotificationRequestedConsumerTest extends MySqlContainerSupport {

    private static final long WAIT_MILLIS = 15_000;

    // MySQL은 상속으로 받으므로 Kafka는 주소만 넣는다.
    // @ImportTestcontainers로 가져오면 이 컨텍스트가 닫힐 때 공유 Kafka 컨테이너가 멈춰 다른 테스트가 실패한다
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaContainerSupport::bootstrapServers);
    }

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationRepository notificationRepository;
    private final DltConsumer dltConsumer;

    @Autowired
    NotificationRequestedConsumerTest(
            KafkaTemplate<String, Object> kafkaTemplate,
            NotificationRepository notificationRepository,
            DltConsumer dltConsumer) {
        this.kafkaTemplate = kafkaTemplate;
        this.notificationRepository = notificationRepository;
        this.dltConsumer = dltConsumer;
    }

    @Test
    @DisplayName("알림 요청 하나를 받으면 메시지 내용 그대로 안 읽은 알림 하나를 저장한다")
    void consume_savesNotification() throws InterruptedException {
        NotificationRequestedMessage message = message(9001L, "flash-drop:39:open");

        send(message);

        List<Notification> saved = waitForNotifications(9001L, 1);
        assertThat(saved).hasSize(1);
        Notification notification = saved.get(0);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.FLASH_DROP);
        assertThat(notification.getContent()).isEqualTo(message.content());
        assertThat(notification.getLinkUrl()).isEqualTo(message.linkUrl());
        assertThat(notification.getDedupeKey()).isEqualTo("flash-drop:39:open");
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("같은 회원에게 같은 중복 방지 키가 두 번 오면 알림은 하나만 남고 DLT로 보내지 않는다")
    void consume_sameDedupeKeyTwice_keepsOne() throws InterruptedException {
        NotificationRequestedMessage duplicated = message(9002L, "flash-drop:40:open");

        send(duplicated);
        send(duplicated);
        // 파티션이 하나라 순서대로 처리되므로, 뒤에 보낸 메시지가 저장되면 앞의 두 개는 처리가 끝난 것이다
        send(message(9002L, "flash-drop:40:announce"));

        List<Notification> saved = waitForNotifications(9002L, 2);
        assertThat(saved).extracting(Notification::getDedupeKey)
                .containsExactlyInAnyOrder("flash-drop:40:open", "flash-drop:40:announce");
        // 중복을 건너뛰지 못하고 다시 던졌다면 재시도 뒤 DLT로 간다
        assertThat(dltConsumer.records.poll(3, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("중복 방지 키가 같아도 회원이 다르면 회원마다 하나씩 저장한다")
    void consume_sameDedupeKeyForOtherMembers_savesEach() throws InterruptedException {
        send(message(9003L, "flash-drop:41:open"));
        send(message(9004L, "flash-drop:41:open"));

        assertThat(waitForNotifications(9003L, 1)).hasSize(1);
        assertThat(waitForNotifications(9004L, 1)).hasSize(1);
    }

    private NotificationRequestedMessage message(Long memberId, String dedupeKey) {
        return new NotificationRequestedMessage(
                memberId, NotificationType.FLASH_DROP, "번개 드롭이 시작됐습니다", "/products/1", dedupeKey);
    }

    private void send(NotificationRequestedMessage message) {
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_REQUESTED, String.valueOf(message.memberId()), message).join();
    }

    // 소비자가 비동기로 저장하므로 기대한 수만큼 쌓일 때까지 기다린 뒤 그 회원의 알림을 돌려준다
    private List<Notification> waitForNotifications(Long memberId, int expectedCount) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        List<Notification> found = findByMemberId(memberId);
        while (found.size() < expectedCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            found = findByMemberId(memberId);
        }
        return found;
    }

    private List<Notification> findByMemberId(Long memberId) {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getMemberId().equals(memberId))
                .toList();
    }

    @TestConfiguration
    static class DltConsumerConfig {

        @Bean
        DltConsumer dltConsumer() {
            return new DltConsumer();
        }
    }

    static class DltConsumer {

        final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

        @KafkaListener(topics = KafkaTopics.NOTIFICATION_REQUESTED + ".DLT", groupId = "test-group-notification-dlt")
        void receive(ConsumerRecord<String, String> record) {
            records.offer(record);
        }
    }
}

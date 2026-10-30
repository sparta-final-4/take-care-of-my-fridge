package org.example.takecareofmyfridgebe.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationListRequest;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationListResponse;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationResponse;
import org.example.takecareofmyfridgebe.domain.notification.dto.ReadAllNotificationsRequest;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.repository.NotificationRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, NotificationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationServiceTransactionTest extends MySqlContainerSupport {

    private static final long MEMBER = 1L;
    private static final long OTHER_MEMBER = 2L;

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    private int sequence;

    @Autowired
    NotificationServiceTransactionTest(NotificationService notificationService,
                                       NotificationRepository notificationRepository) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("내 알림이 6개면 최신 5개를 최신순으로 주고 다음이 있다고 알린다")
    void getMyNotifications_returnsLatestFiveWithNext() {
        List<Notification> mine = saveNotifications(MEMBER, 6);
        saveNotifications(OTHER_MEMBER, 1);

        NotificationListResponse response = notificationService.getMyNotifications(MEMBER, new NotificationListRequest(null));

        assertThat(idsOf(response)).containsExactly(
                mine.get(5).getId(), mine.get(4).getId(), mine.get(3).getId(), mine.get(2).getId(), mine.get(1).getId());
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("내 알림이 딱 5개면 다음이 없다")
    void getMyNotifications_hasNoNextWhenExactlyFive() {
        saveNotifications(MEMBER, 5);

        NotificationListResponse response = notificationService.getMyNotifications(MEMBER, new NotificationListRequest(null));

        assertThat(response.notifications()).hasSize(5);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("더 보기는 화면의 마지막 알림보다 오래된 알림만 겹치지 않게 준다")
    void getMyNotifications_returnsOlderThanBeforeId() {
        List<Notification> mine = saveNotifications(MEMBER, 7);
        saveNotifications(OTHER_MEMBER, 1);
        NotificationListResponse firstPage = notificationService.getMyNotifications(MEMBER, new NotificationListRequest(null));
        Long lastShownId = firstPage.notifications().get(4).notificationId();

        NotificationListResponse nextPage = notificationService.getMyNotifications(MEMBER, new NotificationListRequest(lastShownId));

        assertThat(idsOf(nextPage)).containsExactly(mine.get(1).getId(), mine.get(0).getId());
        assertThat(nextPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("안 읽은 수는 내 안 읽은 알림만 센다")
    void getUnreadCount_countsOnlyMyUnread() {
        saveNotifications(MEMBER, 3);
        Notification read = saveNotifications(MEMBER, 1).get(0);
        read.markRead();
        notificationRepository.save(read);
        saveNotifications(OTHER_MEMBER, 2);

        assertThat(notificationService.getUnreadCount(MEMBER).unreadCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("전부 읽음은 마지막 알림 ID 이하의 내 알림만 바꾸고, 그 뒤 도착한 알림과 다른 회원 알림은 그대로 둔다")
    void readAll_marksOnlyMineUpToLastId() {
        List<Notification> shown = saveNotifications(MEMBER, 2);
        Notification others = saveNotifications(OTHER_MEMBER, 1).get(0);
        Notification arrivedLater = saveNotifications(MEMBER, 1).get(0);

        notificationService.readAll(MEMBER, new ReadAllNotificationsRequest(shown.get(1).getId()));

        assertThat(isRead(shown.get(0))).isTrue();
        assertThat(isRead(shown.get(1))).isTrue();
        assertThat(isRead(arrivedLater)).isFalse();
        assertThat(isRead(others)).isFalse();
    }

    private List<Notification> saveNotifications(Long memberId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> notificationRepository.save(Notification.builder()
                        .memberId(memberId)
                        .notificationType(NotificationType.FLASH_DROP)
                        .content("번개 드롭 알림 " + sequence)
                        .linkUrl("/products/1")
                        .dedupeKey("test:" + sequence++)
                        .build()))
                .toList();
    }

    private List<Long> idsOf(NotificationListResponse response) {
        return response.notifications().stream()
                .map(NotificationResponse::notificationId)
                .toList();
    }

    private boolean isRead(Notification notification) {
        return notificationRepository.findById(notification.getId()).orElseThrow().isRead();
    }
}

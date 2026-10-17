package org.example.takecareofmyfridgebe.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_notifications_member_id_dedupe_key",
            columnNames = {"member_id", "dedupe_key"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 타 도메인(Member)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "content", length = 200, nullable = false)
    private String content;

    @Column(name = "link_url", length = 500, nullable = false)
    private String linkUrl;

    @Column(name = "dedupe_key", length = 150, nullable = false)
    private String dedupeKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Builder
    private Notification(
        Long memberId,
        NotificationType notificationType,
        String content,
        String linkUrl,
        String dedupeKey
    ) {
        validateNotification(memberId, notificationType, content, linkUrl, dedupeKey);

        this.memberId = memberId;
        this.notificationType = notificationType;
        this.content = content;
        this.linkUrl = linkUrl;
        this.dedupeKey = dedupeKey;
        this.read = false;
    }

    // 알림 읽음 처리 (이미 읽은 경우에도 에러 없이 유지)
    public void markRead() {
        this.read = true;
    }

    // 배치·Kafka 경로 생성 시 필수 값(회원 ID, 알림 유형, 내용, 이동 링크, 중복 방지 키) 검증
    private static void validateNotification(
        Long memberId,
        NotificationType type,
        String content,
        String linkUrl,
        String dedupeKey
    ) {
        if (memberId == null || type == null
                || isBlank(content) || isBlank(linkUrl) || isBlank(dedupeKey)) {
            throw new BusinessException(ErrorCode.INVALID_NOTIFICATION);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

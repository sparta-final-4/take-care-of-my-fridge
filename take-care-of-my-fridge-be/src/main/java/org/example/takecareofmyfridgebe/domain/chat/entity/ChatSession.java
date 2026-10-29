package org.example.takecareofmyfridgebe.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "chat_sessions",
    indexes = {
        @Index(name = "idx_chat_sessions_member_id_status", columnList = "member_id, status"),
        @Index(name = "idx_chat_sessions_member_id_last_message_at", columnList = "member_id, last_message_at"),
        @Index(name = "idx_chat_sessions_status_last_message_at", columnList = "status, last_message_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 타 도메인(Member)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChatSessionStatus status;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Builder
    private ChatSession(Long memberId) {
        this.memberId = memberId;
        this.status = ChatSessionStatus.ACTIVE;
        this.lastMessageAt = LocalDateTime.now();
    }

    // 진행 중(ACTIVE)일 때만 마지막 대화 일시 갱신
    public void touch(LocalDateTime now) {
        validateActive();
        this.lastMessageAt = now;
    }

    // 상담 세션 요리 완료로 종료 (ACTIVE 상태에서만 가능)
    public void complete() {
        validateActive();
        this.status = ChatSessionStatus.COMPLETED;
    }

    // 상담 세션 시간 초과로 만료 (ACTIVE 상태에서만 가능)
    public void expire() {
        validateActive();
        this.status = ChatSessionStatus.EXPIRED;
    }

    // 진행 중(ACTIVE) 여부 확인
    public boolean isActive() {
        return this.status == ChatSessionStatus.ACTIVE;
    }

    public boolean isCompleted() {
        return this.status == ChatSessionStatus.COMPLETED;
    }

    // 특정 기준 시점(now)과 타임아웃을 기준으로 만료 여부 판정
    public boolean isExpired(Duration timeout, LocalDateTime now) {
        if (!isActive()) {
            return false;
        }
        return this.lastMessageAt.plus(timeout).isBefore(now);
    }

    private void validateActive() {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_ROOM_STATUS);
        }
    }
}

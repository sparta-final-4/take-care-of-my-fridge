package org.example.takecareofmyfridgebe.domain.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 화면이 받은 가장 최신 알림 ID. 목록을 받은 뒤 도착한 알림은 읽음 처리하지 않는다.
public record ReadAllNotificationsRequest(
        @NotNull(message = "마지막 알림 ID는 필수입니다.")
        @Positive(message = "마지막 알림 ID는 1 이상이어야 합니다.")
        Long lastNotificationId
) {
}

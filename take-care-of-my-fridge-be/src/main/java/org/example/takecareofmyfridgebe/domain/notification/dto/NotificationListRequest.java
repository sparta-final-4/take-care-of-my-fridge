package org.example.takecareofmyfridgebe.domain.notification.dto;

import jakarta.validation.constraints.Positive;

// 처음 열 때는 beforeId 없이, "더 보기"는 화면의 마지막 알림 ID를 보낸다.
public record NotificationListRequest(
        @Positive(message = "기준 알림 ID는 1 이상이어야 합니다.") Long beforeId
) {
}

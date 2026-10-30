package org.example.takecareofmyfridgebe.domain.notification.event;

import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;

// 알림 요청 토픽에 싣는 메시지. 회원 한 명에게 보낼 알림 하나이며, 알림 테이블의 칸과 같다.
// 같은 회원에게 같은 dedupeKey가 두 번 오면 알림은 하나만 남아야 한다
public record NotificationRequestedMessage(
        Long memberId,
        NotificationType notificationType,
        String content,
        String linkUrl,
        String dedupeKey
) {
}

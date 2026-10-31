package org.example.takecareofmyfridgebe.domain.notification.push;

// Redis 채널에 싣는 메시지. 어느 서버에 그 회원의 연결이 있는지 모르므로 모든 서버가 받는다.
// 알림 내용은 싣지 않는다. 화면이 인증된 조회 API로 직접 받아간다
public record NotificationPushMessage(
        Long memberId
) {
}

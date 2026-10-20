package org.example.takecareofmyfridgebe.infra.portone.webhook.entity;

public enum WebhookStatus {
    RECEIVED,  // 수신 후 처리 중
    PROCESSED, // 결제 상태 반영 완료
    FAILED,    // 처리 중 오류, PortOne 재전송 시 다시 처리
    IGNORED    // 처리 대상이 아니거나 바꿀 상태가 없음
}

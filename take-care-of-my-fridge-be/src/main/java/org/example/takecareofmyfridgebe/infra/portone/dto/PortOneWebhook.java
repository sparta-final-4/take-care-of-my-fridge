package org.example.takecareofmyfridgebe.infra.portone.dto;

// 서명 검증을 통과한 웹훅 중 결제 처리에 쓰는 값만 담는다. 결제 관련이 아닌 웹훅은 paymentId가 null
public record PortOneWebhook(
        String type,
        String paymentId
) {
}

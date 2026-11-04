package org.example.takecareofmyfridgebe.infra.portone.dto;

// 서명 검증을 통과한 웹훅 중 처리에 쓰는 값만 담는다.
// 결제 웹훅은 paymentId, 빌링키 웹훅은 billingKey가 있고 나머지는 null
public record PortOneWebhook(
        String type,
        String paymentId,
        String billingKey
) {
    public PortOneWebhook(String type, String paymentId) {
        this(type, paymentId, null);
    }
}

package org.example.takecareofmyfridgebe.domain.payment.dto;

// 프론트가 PortOne 카드 등록 창(requestIssueBillingKey)을 여는 데 쓰는 값
public record BillingKeyPrepareResponse(
        String channelKey,  // 정기결제 채널 키
        String issueId,     // 이번 발급 요청 ID
        String issueName,   // 카드 등록 창에 보이는 제목
        String customerId   // 등록 확정 때 본인 카드인지 확인하는 고객 ID
) {
}

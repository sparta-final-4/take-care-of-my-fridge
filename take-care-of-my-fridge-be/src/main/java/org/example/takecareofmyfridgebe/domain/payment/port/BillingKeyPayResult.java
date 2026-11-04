package org.example.takecareofmyfridgebe.domain.payment.port;

// 등록 카드 결제 요청의 결과. 결제됐다고 해도 금액·결제 시각은 PG 결제 조회로 다시 확인한다
public enum BillingKeyPayResult {
    PAID,     // PG가 결제를 마쳤다(이미 결제된 결제 ID 포함)
    REJECTED, // 카드 거절·빌링키 없음 등으로 PG가 결제하지 않았다
    UNKNOWN   // 타임아웃·PG 서버 오류로 결제됐는지 알 수 없다
}

package org.example.takecareofmyfridgebe.domain.payment.port;

import java.util.Optional;

public interface BillingKeyGateway {

    // 카드 등록 창을 여는 정기결제 채널 키
    String billingChannelKey();

    // PG에 빌링키가 없으면 빈 값. 조회 자체가 실패하면(타임아웃·PG 오류) 예외를 던진다
    Optional<BillingKeyInfo> getBillingKey(String billingKey);

    // 이미 삭제된 빌링키면 성공으로 본다. 그 밖의 실패는 예외를 던진다
    void deleteBillingKey(String billingKey, String reason);

    // 결제창 없이 등록 카드로 청구한다. 결제 ID가 멱등 키라 같은 결제는 PG에서 한 번만 청구된다.
    // 예외를 던지지 않고 결과(결제됨·거절·모름)로 돌려준다
    BillingKeyPayResult pay(String paymentId, String billingKey, String orderName, long amount, String customerId);
}

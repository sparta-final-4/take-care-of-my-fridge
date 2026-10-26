package org.example.takecareofmyfridgebe.domain.payment.port;

import java.util.Optional;
import java.util.UUID;

public interface PaymentGateway {

    // PG사에서 실제 결제 정보 조회 (금액 검증용). 결제창을 열지 않아 PG에 결제가 없으면 빈 값
    Optional<PaymentGatewayResponse> getPayment(String paymentId);

    // 결제 취소 (amount가 null이면 전액 취소, 값이 있으면 부분 취소). PG 취소 건 ID를 돌려준다.
    // 이미 취소된 결제라 새 취소 건이 생기지 않았으면 null.
    // 같은 idempotencyKey로 다시 요청하면 PG는 한 번만 취소한다.
    // PG가 취소를 거절하면 BusinessException(PG_CANCEL_REJECTED), 결과를 알 수 없으면(타임아웃·PG 서버 오류) 그 외 예외를 던진다
    String cancelPayment(String paymentId, String reason, Long amount, String idempotencyKey);

    // 호출마다 새 멱등 키를 쓴다. 한 번의 호출 안에서 재시도할 때만 같은 키가 쓰인다
    default String cancelPayment(String paymentId, String reason, Long amount) {
        return cancelPayment(paymentId, reason, amount, UUID.randomUUID().toString());
    }
}

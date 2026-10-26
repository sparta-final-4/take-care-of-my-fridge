package org.example.takecareofmyfridgebe.domain.payment.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// description은 결제 이력의 사유로 남긴다
@Getter
@RequiredArgsConstructor
public enum FailReason {
    PG_FAILED("PG 결제 실패"),                        // PG 결제 실패·거절
    USER_CANCELLED("사용자 결제창 취소"),              // 사용자가 결제창에서 취소
    AMOUNT_MISMATCH("결제 금액 불일치"),               // PG 결제 금액과 주문 금액 불일치
    TIMEOUT("결제 대기 시간 초과"),                    // 결제 대기 시간 초과
    ORDER_NOT_PAYABLE("결제할 수 없는 주문"),          // PG 결제는 끝났지만 주문이 이미 결제 완료·취소돼 받을 수 없음
    SUPERSEDED("새 결제 시작으로 이전 결제 대기 정리"); // 같은 주문에 새 결제를 시작하면서 남아 있던 결제 대기 건을 정리함

    private final String description;
}

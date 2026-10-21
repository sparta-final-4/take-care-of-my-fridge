package org.example.takecareofmyfridgebe.domain.payment.entity;

public enum FailReason {
    PG_FAILED,         // PG 결제 실패·거절
    USER_CANCELLED,    // 사용자가 결제창에서 취소
    AMOUNT_MISMATCH,   // PG 결제 금액과 주문 금액 불일치
    TIMEOUT,           // 결제 대기 시간 초과
    ORDER_NOT_PAYABLE, // PG 결제는 끝났지만 주문이 이미 결제 완료·취소돼 받을 수 없음
    SUPERSEDED         // 같은 주문에 새 결제를 시작하면서 남아 있던 결제 대기 건을 정리함
}

package org.example.takecareofmyfridgebe.domain.payment.entity;

public enum FailReason {
    PG_FAILED,       // PG 결제 실패·거절
    USER_CANCELLED,  // 사용자가 결제창에서 취소
    AMOUNT_MISMATCH, // PG 결제 금액과 주문 금액 불일치
    TIMEOUT          // 결제 대기 시간 초과
}

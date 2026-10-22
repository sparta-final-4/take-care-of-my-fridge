package org.example.takecareofmyfridgebe.domain.refund.entity;

public enum RefundStatus {
    REQUESTED, // 환불 요청됨 (PG 취소 대기)
    COMPLETED, // PG 취소 완료
    FAILED     // PG 취소 실패
}

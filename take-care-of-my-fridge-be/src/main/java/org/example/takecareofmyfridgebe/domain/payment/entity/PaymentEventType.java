package org.example.takecareofmyfridgebe.domain.payment.entity;

// 결제 이력에 남기는 사건. 결제 상태(PaymentStatus)와 별개로, 취소는 PG 기준이다
public enum PaymentEventType {
    READY,             // 결제 시도 생성
    PAID,              // PG에서 돈이 결제됨. 받지 않은 결제(실패 처리 후 PG 승인)도 보상 취소 대상임을 남기려고 기록한다
    FAILED,            // 결제 시도 실패
    PARTIAL_CANCELLED, // PG 부분 취소 (환불·콘솔 취소)
    CANCELLED          // PG 전액 취소 (전액 환불·보상 취소·콘솔 취소)
}

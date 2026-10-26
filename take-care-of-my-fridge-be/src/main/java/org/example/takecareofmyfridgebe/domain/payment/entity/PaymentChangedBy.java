package org.example.takecareofmyfridgebe.domain.payment.entity;

// 결제 이력의 사건을 일으킨 주체
public enum PaymentChangedBy {
    USER,      // 사용자 요청 (결제하기, 결제 확정, 결제창 닫기, 환불 신청)
    WEBHOOK,   // PortOne 웹훅
    SCHEDULER  // 결제 만료·보상 취소 재시도·환불 정리 스케줄러
}

package org.example.takecareofmyfridgebe.domain.payment.entity;

// 등록 카드 종류. PortOne 빌링키 조회 값이며, PortOne이 주지 않으면 null로 둔다
public enum CardType {
    CREDIT, // 신용
    DEBIT,  // 체크
    GIFT    // 기프트
}

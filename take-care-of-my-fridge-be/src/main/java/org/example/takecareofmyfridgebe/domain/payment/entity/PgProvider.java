package org.example.takecareofmyfridgebe.domain.payment.entity;

// 사용자가 고르는 결제대행사. PortOne 채널 하나와 짝을 이룬다
public enum PgProvider {
    KAKAOPAY,     // 카카오페이(간편결제)
    KICC,         // 이지페이
    INICIS,       // KG이니시스
    TOSSPAYMENTS, // 토스페이먼츠
    TOSSPAY       // 토스페이(간편결제)
}

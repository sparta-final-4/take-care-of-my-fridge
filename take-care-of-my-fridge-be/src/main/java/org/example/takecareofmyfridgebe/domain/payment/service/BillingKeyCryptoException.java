package org.example.takecareofmyfridgebe.domain.payment.service;

// 빌링키 암호화·복호화·지문 실패. 키 설정 오류나 저장 값 손상처럼 서버 쪽 문제다.
// 회원 입력 오류(400)로 처리되지 않도록 IllegalStateException이 아니라 RuntimeException을 잇는다. 따로 잡지 않으면 500으로 가려진다.
// 메시지에 빌링키 원문과 키 값은 넣지 않는다(키 ID만 넣을 수 있다)
public class BillingKeyCryptoException extends RuntimeException {

    public BillingKeyCryptoException(String message) {
        super(message);
    }

    public BillingKeyCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

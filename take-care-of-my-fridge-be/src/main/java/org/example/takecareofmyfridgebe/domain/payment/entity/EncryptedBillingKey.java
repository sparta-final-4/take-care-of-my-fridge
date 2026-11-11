package org.example.takecareofmyfridgebe.domain.payment.entity;

// 암호화한 빌링키. 암호문과 지문은 항상 같이 저장하므로 한 값으로 묶어 넘긴다.
// toString은 record 기본값이지만 둘 다 원문이 아니어서 로그에 찍혀도 빌링키가 드러나지 않는다
public record EncryptedBillingKey(String ciphertext, String hash) {

    public EncryptedBillingKey {
        if (ciphertext == null || ciphertext.isBlank() || hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("빌링키 암호문과 지문이 모두 있어야 합니다.");
        }
    }
}

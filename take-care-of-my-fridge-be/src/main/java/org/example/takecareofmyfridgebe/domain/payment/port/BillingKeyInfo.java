package org.example.takecareofmyfridgebe.domain.payment.port;

import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;

// PG에서 조회한 빌링키. 카드 정보는 PortOne이 주지 않으면 null이다
public record BillingKeyInfo(
        String billingKey,
        boolean issued,     // 발급 완료 상태면 true, 삭제됐으면 false
        String customerId,  // 발급 때 넘긴 고객 ID
        String cardName,    // 카드 상품명
        String cardNumber,  // 마스킹된 카드 번호
        CardType cardType,
        String cardIssuerCode // 발급사 코드. 같은 카드 중복 등록 확인에 쓴다
) {
    private static final int LAST_DIGITS = 4;

    // 마스킹된 번호의 마지막 4글자가 모두 숫자일 때만 끝 4자리로 본다
    public String cardLast4() {
        if (cardNumber == null || cardNumber.length() < LAST_DIGITS) {
            return null;
        }
        String last = cardNumber.substring(cardNumber.length() - LAST_DIGITS);
        return last.chars().allMatch(Character::isDigit) ? last : null;
    }
}

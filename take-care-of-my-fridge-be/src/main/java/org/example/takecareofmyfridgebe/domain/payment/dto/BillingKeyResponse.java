package org.example.takecareofmyfridgebe.domain.payment.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;

import java.time.LocalDateTime;

// 등록 카드. 빌링키 값은 내려주지 않는다
public record BillingKeyResponse(
        Long cardId,
        String cardName,
        String cardLast4,
        CardType cardType,
        boolean isDefault,
        LocalDateTime createdAt
) {
    public static BillingKeyResponse from(BillingKey billingKey) {
        return new BillingKeyResponse(
                billingKey.getId(),
                billingKey.getCardName(),
                billingKey.getCardLast4(),
                billingKey.getCardType(),
                billingKey.isDefault(),
                billingKey.getCreatedAt());
    }
}

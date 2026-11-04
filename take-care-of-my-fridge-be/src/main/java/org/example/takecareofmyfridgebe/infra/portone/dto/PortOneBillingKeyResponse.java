package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;

import java.util.Arrays;
import java.util.List;

// PortOne V2 빌링키 단건 조회 응답 중 등록 확인과 카드 표시에 쓰는 필드만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingKeyResponse(
        String status,         // ISSUED 또는 DELETED
        List<Method> methods,  // 발급에 쓴 결제수단. 카드면 type이 BillingKeyPaymentMethodCard
        Customer customer
) {
    private static final String STATUS_ISSUED = "ISSUED";
    private static final String METHOD_CARD = "BillingKeyPaymentMethodCard";

    // 저장할 빌링키는 응답 본문 값이 아니라 우리가 조회한 값을 쓴다. 응답에 빌링키가 빠져도 확인한 키가 저장된다
    public BillingKeyInfo toInfo(String requestedBillingKey) {
        Card card = methods == null ? null : methods.stream()
                .filter(method -> METHOD_CARD.equals(method.type()) && method.card() != null)
                .map(Method::card)
                .findFirst()
                .orElse(null);
        return new BillingKeyInfo(
                requestedBillingKey,
                STATUS_ISSUED.equals(status),
                customer == null ? null : customer.id(),
                card == null ? null : card.name(),
                card == null ? null : card.number(),
                card == null ? null : toCardType(card.type()));
    }

    // 우리가 모르는 카드 종류가 와도 등록은 막지 않고 종류만 비워 둔다
    private static CardType toCardType(String type) {
        return Arrays.stream(CardType.values())
                .filter(cardType -> cardType.name().equals(type))
                .findFirst()
                .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(String type, Card card) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String name, String number, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id) {
    }
}

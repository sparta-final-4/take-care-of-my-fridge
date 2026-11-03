package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// PortOne V2 결제 단건 조회 응답 중 검증과 환불 대조에 쓰는 필드만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String status,
        Amount amount,
        Instant paidAt,
        List<Cancellation> cancellations, // 취소·부분 취소된 결제에만 있다
        Channel channel // 결제창을 연 채널. 결제를 시작하기 전이면 없을 수 있다
) {
    private static final String CANCELLATION_SUCCEEDED = "SUCCEEDED";
    private static final String PG_PROVIDER_PREFIX = "PG_PROVIDER_";

    public PaymentGatewayResponse toGatewayResponse() {
        return new PaymentGatewayResponse(
                id,
                status,
                amount == null ? null : amount.total(),
                amount == null || amount.cancelled() == null ? 0L : amount.cancelled(),
                // 만료 시각(expires_at)이 서버 시간대의 LocalDateTime으로 저장되므로 같은 기준으로 바꿔 비교한다
                paidAt == null ? null : LocalDateTime.ofInstant(paidAt, ZoneId.systemDefault()),
                cancellations == null ? List.of() : cancellations.stream()
                        .map(cancellation -> new PaymentGatewayResponse.Cancellation(
                                cancellation.id(),
                                cancellation.totalAmount() == null ? 0L : cancellation.totalAmount(),
                                CANCELLATION_SUCCEEDED.equals(cancellation.status())))
                        .toList(),
                channel == null ? null : toPgProvider(channel.pgProvider())
        );
    }

    // PortOne 결제대행사 코드를 우리가 고를 수 있는 결제대행사로 바꾼다. 쓰지 않는 결제대행사면 null
    private static PgProvider toPgProvider(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.startsWith(PG_PROVIDER_PREFIX) ? code.substring(PG_PROVIDER_PREFIX.length()) : code;
        return switch (normalized) {
            case "KAKAOPAY" -> PgProvider.KAKAOPAY;
            case "KICC" -> PgProvider.KICC;
            case "INICIS", "INICIS_V2" -> PgProvider.INICIS;
            case "TOSSPAYMENTS" -> PgProvider.TOSSPAYMENTS;
            case "TOSSPAY", "TOSSPAY_V2" -> PgProvider.TOSSPAY;
            default -> null;
        };
    }

    // Jackson 3는 원시 타입 필드가 응답에 없으면 파싱을 실패시키므로, 없을 수 있는 값은 래퍼 타입으로 받는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            long total,
            Long cancelled
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(
            String pgProvider
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
            String status,
            String id,
            Long totalAmount
    ) {
    }
}

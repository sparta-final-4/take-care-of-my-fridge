package org.example.takecareofmyfridgebe.infra.portone.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneCancelRequest;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneErrorResponse;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOnePaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient implements PaymentGateway {

    private static final int MAX_CANCEL_ATTEMPTS = 3;
    private static final String ALREADY_CANCELLED_ERROR_TYPE = "PAYMENT_ALREADY_CANCELLED";

    private final RestClient portOneRestClient;
    private final PortOneProperties portOneProperties;

    // 결제창에서 결제를 시작하지 않았으면 PortOne에 결제 건이 없어 404가 온다
    @Override
    public Optional<PaymentGatewayResponse> getPayment(String paymentId) {
        log.info("PortOne 결제 조회: paymentId={}", paymentId);
        try {
            return Optional.ofNullable(portOneRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/payments/{paymentId}")
                            .queryParam("storeId", portOneProperties.storeId())
                            .build(paymentId))
                    .retrieve()
                    .body(PortOnePaymentResponse.class))
                    .map(PortOnePaymentResponse::toGatewayResponse);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // amount가 null이면 전액 취소한다
    @Override
    public void cancelPayment(String paymentId, String reason, Long amount) {
        // 재시도해도 한 번만 취소되도록 모든 시도에 같은 키를 쓴다
        String idempotencyKey = UUID.randomUUID().toString();
        log.info("PortOne 결제 취소 요청: paymentId={}, reason={}, idempotencyKey={}", paymentId, reason, idempotencyKey);

        for (int attempt = 1; attempt <= MAX_CANCEL_ATTEMPTS; attempt++) {
            try {
                portOneRestClient.post()
                        .uri("/payments/{paymentId}/cancel", paymentId)
                        .header("Idempotency-Key", "\"" + idempotencyKey + "\"")
                        .body(new PortOneCancelRequest(amount, reason, portOneProperties.storeId()))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException e) {
                // 다른 요청이 먼저 취소했으면 원하는 결과(취소됨)는 이미 이뤄졌으므로 성공으로 본다
                if (isAlreadyCancelled(e)) {
                    log.info("PortOne 결제가 이미 취소됨: paymentId={}", paymentId);
                    return;
                }
                throw e;
            } catch (ResourceAccessException e) {
                // 네트워크 타임아웃만 재시도한다
                log.warn("PortOne 취소 요청 타임아웃 (시도 {}/{}): paymentId={}", attempt, MAX_CANCEL_ATTEMPTS, paymentId);
                if (attempt == MAX_CANCEL_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private boolean isAlreadyCancelled(HttpClientErrorException e) {
        try {
            PortOneErrorResponse error = e.getResponseBodyAs(PortOneErrorResponse.class);
            return error != null && ALREADY_CANCELLED_ERROR_TYPE.equals(error.type());
        } catch (RuntimeException parseError) {
            // 오류 본문을 해석할 수 없으면 이미 취소된 것으로 단정하지 않는다
            return false;
        }
    }
}

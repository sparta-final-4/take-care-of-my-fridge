package org.example.takecareofmyfridgebe.infra.portone.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneCancelRequest;
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
public class PortOneClient {

    private static final int MAX_CANCEL_ATTEMPTS = 3;

    private final RestClient portOneRestClient;
    private final PortOneProperties portOneProperties;

    // 결제창에서 결제를 시작하지 않았으면 PortOne에 결제 건이 없어 404가 온다
    public Optional<PortOnePaymentResponse> getPayment(String paymentId) {
        log.info("PortOne 결제 조회: paymentId={}", paymentId);
        try {
            return Optional.ofNullable(portOneRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/payments/{paymentId}")
                            .queryParam("storeId", portOneProperties.storeId())
                            .build(paymentId))
                    .retrieve()
                    .body(PortOnePaymentResponse.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // amount가 null이면 전액 취소한다
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
            } catch (ResourceAccessException e) {
                // 네트워크 타임아웃만 재시도한다
                log.warn("PortOne 취소 요청 타임아웃 (시도 {}/{}): paymentId={}", attempt, MAX_CANCEL_ATTEMPTS, paymentId);
                if (attempt == MAX_CANCEL_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}

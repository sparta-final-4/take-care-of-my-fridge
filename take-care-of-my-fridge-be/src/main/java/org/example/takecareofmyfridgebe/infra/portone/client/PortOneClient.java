package org.example.takecareofmyfridgebe.infra.portone.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneCancelRequest;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneCancelResponse;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneErrorResponse;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOnePaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

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

    // amount가 null이면 전액 취소한다. 재시도해도 한 번만 취소되도록 모든 시도에 같은 멱등 키를 쓴다
    @Override
    public String cancelPayment(String paymentId, String reason, Long amount, String idempotencyKey) {
        log.info("PortOne 결제 취소 요청: paymentId={}, amount={}, reason={}, idempotencyKey={}",
                paymentId, amount, reason, idempotencyKey);

        for (int attempt = 1; ; attempt++) {
            try {
                PortOneCancelResponse response = portOneRestClient.post()
                        .uri("/payments/{paymentId}/cancel", paymentId)
                        .header("Idempotency-Key", "\"" + idempotencyKey + "\"")
                        .body(new PortOneCancelRequest(amount, reason, portOneProperties.storeId()))
                        .retrieve()
                        .body(PortOneCancelResponse.class);
                return response == null ? null : response.cancellationId();
            } catch (HttpClientErrorException e) {
                // 전액 취소는 다른 요청이 먼저 취소했어도 원하는 결과(취소됨)가 이뤄졌으므로 성공으로 본다.
                // 부분 취소는 이번 금액이 환불되지 않았으므로 거절로 본다
                PortOneErrorResponse error = readError(e);
                if (amount == null && error != null && ALREADY_CANCELLED_ERROR_TYPE.equals(error.type())) {
                    log.info("PortOne 결제가 이미 취소됨: paymentId={}", paymentId);
                    return null;
                }
                // 4xx는 PG가 요청을 받고 거절한 것이라 취소가 일어나지 않았다
                log.warn("PortOne 취소 거절: paymentId={}, status={}, body={}", paymentId, e.getStatusCode(),
                        e.getResponseBodyAsString());
                throw new BusinessException(ErrorCode.PG_CANCEL_REJECTED, rejectionMessage(e, error));
            } catch (ResourceAccessException e) {
                // 네트워크 타임아웃만 재시도한다. 마지막 시도도 실패하면 취소됐는지 알 수 없으므로 그대로 던진다
                log.warn("PortOne 취소 요청 타임아웃 (시도 {}/{}): paymentId={}", attempt, MAX_CANCEL_ATTEMPTS, paymentId);
                if (attempt >= MAX_CANCEL_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    // 오류 본문을 해석할 수 없으면 null. 이 경우 이미 취소된 것으로 단정하지 않는다
    private PortOneErrorResponse readError(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(PortOneErrorResponse.class);
        } catch (RuntimeException parseError) {
            return null;
        }
    }

    // 오류 본문을 해석하지 못해도 원문을 남겨 실패 원인을 알 수 있게 한다
    private String rejectionMessage(HttpClientErrorException e, PortOneErrorResponse error) {
        if (error == null) {
            return "PG 취소 거절: " + e.getStatusCode() + " " + e.getResponseBodyAsString();
        }
        return "PG 취소 거절: " + error.type() + " " + error.message();
    }
}

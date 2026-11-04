package org.example.takecareofmyfridgebe.infra.portone.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyPayResult;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneBillingKeyPaymentRequest;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneBillingKeyResponse;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneErrorResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

// PortOne V2 빌링키 조회·삭제·결제. 빌링키 값은 로그에 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneBillingKeyClient implements BillingKeyGateway {

    private static final String ALREADY_DELETED_ERROR_TYPE = "BILLING_KEY_ALREADY_DELETED";
    private static final String NOT_FOUND_ERROR_TYPE = "BILLING_KEY_NOT_FOUND";
    private static final String DELETE_REQUESTER = "CUSTOMER";
    private static final String ALREADY_PAID_ERROR_TYPE = "ALREADY_PAID";
    private static final String PG_PROVIDER_ERROR_TYPE = "PG_PROVIDER";

    private final RestClient portOneRestClient;
    private final PortOneProperties portOneProperties;

    @Override
    public String billingChannelKey() {
        return portOneProperties.billingChannelKey();
    }

    @Override
    public Optional<BillingKeyInfo> getBillingKey(String billingKey) {
        try {
            return Optional.ofNullable(portOneRestClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/billing-keys/{billingKey}")
                                    .queryParam("storeId", portOneProperties.storeId())
                                    .build(billingKey))
                            .retrieve()
                            .body(PortOneBillingKeyResponse.class))
                    .map(response -> response.toInfo(billingKey));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteBillingKey(String billingKey, String reason) {
        try {
            portOneRestClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/billing-keys/{billingKey}")
                            .queryParam("storeId", portOneProperties.storeId())
                            .queryParam("reason", reason)
                            .queryParam("requester", DELETE_REQUESTER)
                            .build(billingKey))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // 이미 삭제됐거나 PortOne에 없는 빌링키는 원하는 결과(남아 있지 않음)가 이뤄졌으므로 성공으로 본다.
            // 없는 빌링키를 실패로 두면 재시도 스케줄러가 끝없이 다시 지우려 한다
            String errorType = errorType(e);
            if (ALREADY_DELETED_ERROR_TYPE.equals(errorType) || NOT_FOUND_ERROR_TYPE.equals(errorType)) {
                log.info("PortOne 빌링키가 이미 없음: type={}", errorType);
                return;
            }
            log.warn("PortOne 빌링키 삭제 거절: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    @Override
    public BillingKeyPayResult pay(String paymentId, String billingKey, String orderName, long amount,
                                   String customerId) {
        log.info("PortOne 빌링키 결제 요청: paymentId={}, amount={}", paymentId, amount);
        try {
            portOneRestClient.post()
                    .uri("/payments/{paymentId}/billing-key", paymentId)
                    .body(PortOneBillingKeyPaymentRequest.of(portOneProperties.storeId(), billingKey, orderName, amount,
                            customerId))
                    .retrieve()
                    .toBodilessEntity();
            return BillingKeyPayResult.PAID;
        } catch (HttpClientErrorException e) {
            // 같은 결제 ID로 이미 결제됐으면 결제된 것이다
            if (ALREADY_PAID_ERROR_TYPE.equals(errorType(e))) {
                log.info("PortOne 빌링키 결제가 이미 완료됨: paymentId={}", paymentId);
                return BillingKeyPayResult.PAID;
            }
            // 요청 시간 초과·요청 과다는 요청이 처리됐는지 알 수 없다
            int status = e.getStatusCode().value();
            if (status == 408 || status == 429) {
                log.warn("PortOne 빌링키 결제 결과 모름: paymentId={}, status={}", paymentId, status);
                return BillingKeyPayResult.UNKNOWN;
            }
            // 그 밖의 4xx는 PortOne이 요청을 받고 결제하지 않은 것이다
            log.warn("PortOne 빌링키 결제 거절: paymentId={}, status={}, body={}", paymentId, e.getStatusCode(),
                    e.getResponseBodyAsString());
            return BillingKeyPayResult.REJECTED;
        } catch (HttpServerErrorException e) {
            // PG가 거절을 돌려준 경우(카드 한도 초과·정지 카드 등)는 PortOne이 502 PG_PROVIDER로 알린다
            if (PG_PROVIDER_ERROR_TYPE.equals(errorType(e))) {
                log.warn("PG가 빌링키 결제 거절: paymentId={}, body={}", paymentId, e.getResponseBodyAsString());
                return BillingKeyPayResult.REJECTED;
            }
            log.warn("PortOne 빌링키 결제 결과 모름(서버 오류): paymentId={}, status={}", paymentId, e.getStatusCode());
            return BillingKeyPayResult.UNKNOWN;
        } catch (ResourceAccessException e) {
            log.warn("PortOne 빌링키 결제 결과 모름(타임아웃): paymentId={}", paymentId);
            return BillingKeyPayResult.UNKNOWN;
        } catch (RestClientException e) {
            // 알 수 없는 상태 코드 등 그 밖의 통신 오류도 결제됐는지 모르는 것으로 본다
            log.warn("PortOne 빌링키 결제 결과 모름(통신 오류): paymentId={}", paymentId, e);
            return BillingKeyPayResult.UNKNOWN;
        }
    }

    // 오류 본문을 해석할 수 없으면 null. 이 경우 이미 삭제된 것으로 단정하지 않는다
    private String errorType(HttpStatusCodeException e) {
        try {
            PortOneErrorResponse error = e.getResponseBodyAs(PortOneErrorResponse.class);
            return error == null ? null : error.type();
        } catch (RuntimeException parseError) {
            return null;
        }
    }
}

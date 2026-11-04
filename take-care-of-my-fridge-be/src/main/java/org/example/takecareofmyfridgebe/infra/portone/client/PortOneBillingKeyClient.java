package org.example.takecareofmyfridgebe.infra.portone.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneBillingKeyResponse;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneErrorResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

// PortOne V2 빌링키 조회·삭제. 빌링키 값은 로그에 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneBillingKeyClient implements BillingKeyGateway {

    private static final String ALREADY_DELETED_ERROR_TYPE = "BILLING_KEY_ALREADY_DELETED";
    private static final String DELETE_REQUESTER = "CUSTOMER";

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
            // 다른 요청이 먼저 지웠어도 원하는 결과(삭제됨)가 이뤄졌으므로 성공으로 본다
            if (ALREADY_DELETED_ERROR_TYPE.equals(errorType(e))) {
                log.info("PortOne 빌링키가 이미 삭제됨");
                return;
            }
            log.warn("PortOne 빌링키 삭제 거절: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    // 오류 본문을 해석할 수 없으면 null. 이 경우 이미 삭제된 것으로 단정하지 않는다
    private String errorType(HttpClientErrorException e) {
        try {
            PortOneErrorResponse error = e.getResponseBodyAs(PortOneErrorResponse.class);
            return error == null ? null : error.type();
        } catch (RuntimeException parseError) {
            return null;
        }
    }
}

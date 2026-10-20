package org.example.takecareofmyfridgebe.infra.portone.webhook;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneWebhook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneWebhookVerifier {

    private final WebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    // 서명이 맞지 않으면 기록하지 않고 로그만 남긴다
    public PortOneWebhook verify(String body, String webhookId, String signature, String timestamp) {
        Webhook webhook;
        try {
            webhook = webhookVerifier.verify(body, webhookId, signature, timestamp);
        } catch (WebhookVerificationException e) {
            log.warn("PortOne 웹훅 서명 검증 실패: webhookId={}, reason={}", webhookId, e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        String paymentId = webhook instanceof WebhookTransaction transaction
                ? transaction.getData().getPaymentId()
                : null;
        // SDK가 모르는 type은 Unrecognized로 바뀌므로 원문의 type을 그대로 기록한다
        String type = objectMapper.readTree(body).path("type").asString();
        return new PortOneWebhook(type, paymentId);
    }
}

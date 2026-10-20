package org.example.takecareofmyfridgebe.infra.portone.webhook;

import io.portone.sdk.server.webhook.WebhookVerifier;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneWebhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 SDK 검증기에 Standard Webhooks 방식으로 서명한 요청을 넣어 확인한다
class PortOneWebhookVerifierTest {

    private static final byte[] SECRET = "test-webhook-secret-key-32bytes!".getBytes(StandardCharsets.UTF_8);
    private static final String WEBHOOK_ID = "webhook-1";
    private static final String PAID_BODY = """
            {"type":"Transaction.Paid","timestamp":"2026-10-20T01:00:00Z","data":{"paymentId":"payment-1","storeId":"store-1","transactionId":"tx-1"}}""";

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(
            new WebhookVerifier("whsec_" + Base64.getEncoder().encodeToString(SECRET)), new ObjectMapper());

    private static String now() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    private static String sign(String webhookId, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        byte[] signature = mac.doFinal((webhookId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }

    @Test
    @DisplayName("서명이 맞으면 type과 paymentId를 꺼낸다")
    void verify_validSignature() throws Exception {
        String timestamp = now();

        PortOneWebhook webhook = verifier.verify(PAID_BODY, WEBHOOK_ID, sign(WEBHOOK_ID, timestamp, PAID_BODY), timestamp);

        assertThat(webhook.type()).isEqualTo("Transaction.Paid");
        assertThat(webhook.paymentId()).isEqualTo("payment-1");
    }

    @Test
    @DisplayName("SDK가 모르는 type도 원문 type을 그대로 돌려준다")
    void verify_unknownType() throws Exception {
        String body = "{\"type\":\"Transaction.Unknown\",\"data\":{}}";
        String timestamp = now();

        PortOneWebhook webhook = verifier.verify(body, WEBHOOK_ID, sign(WEBHOOK_ID, timestamp, body), timestamp);

        assertThat(webhook.type()).isEqualTo("Transaction.Unknown");
        assertThat(webhook.paymentId()).isNull();
    }

    @Test
    @DisplayName("본문이 바뀌면 서명 검증에 실패한다")
    void verify_tamperedBody() throws Exception {
        String timestamp = now();
        String signature = sign(WEBHOOK_ID, timestamp, PAID_BODY);
        String tampered = PAID_BODY.replace("payment-1", "payment-2");

        assertThatThrownBy(() -> verifier.verify(tampered, WEBHOOK_ID, signature, timestamp))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    @DisplayName("서명 헤더가 없으면 검증에 실패한다")
    void verify_missingHeaders() {
        assertThatThrownBy(() -> verifier.verify(PAID_BODY, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    @DisplayName("5분이 넘은 웹훅은 서명이 맞아도 거절한다")
    void verify_oldTimestamp() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000 - 600);
        String signature = sign(WEBHOOK_ID, timestamp, PAID_BODY);

        assertThatThrownBy(() -> verifier.verify(PAID_BODY, WEBHOOK_ID, signature, timestamp))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }
}

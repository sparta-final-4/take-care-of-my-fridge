package org.example.takecareofmyfridgebe.infra.portone.webhook.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneWebhook;
import org.example.takecareofmyfridgebe.infra.portone.webhook.PortOneWebhookVerifier;
import org.example.takecareofmyfridgebe.infra.portone.webhook.service.WebhookHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PortOneWebhookVerifier portOneWebhookVerifier;
    private final WebhookHandler webhookHandler;

    // PortOne 서버가 호출한다. 서명 검증을 위해 본문을 원문 그대로 받고,
    // 헤더가 없거나 서명이 틀리면 verifier가 401로 응답해 PortOne이 재전송하게 한다
    @PostMapping("/portone")
    public ResponseEntity<ApiResponse<Void>> handlePortOneWebhook(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSignature,
            @RequestBody String body) {
        log.info("[Webhook] received id={} timestamp={}", webhookId, webhookTimestamp);

        PortOneWebhook webhook = portOneWebhookVerifier.verify(body, webhookId, webhookSignature, webhookTimestamp);
        webhookHandler.handle(webhookId, webhook, body);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}

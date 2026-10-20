package org.example.takecareofmyfridgebe.infra.portone.webhook.controller;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.error.GlobalExceptionHandler;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneWebhook;
import org.example.takecareofmyfridgebe.infra.portone.webhook.PortOneWebhookVerifier;
import org.example.takecareofmyfridgebe.infra.portone.webhook.service.WebhookHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebhookController의 요청 바인딩과 응답 변환을 확인한다.
 * 보안 설정(로그인·CSRF 예외)은 SecurityConfig 담당 범위라 이 테스트에서는 다루지 않는다.
 */
class WebhookControllerTest {

    private static final String BODY = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"payment-1\"}}";

    private final PortOneWebhookVerifier verifier = mock(PortOneWebhookVerifier.class);
    private final WebhookHandler webhookHandler = mock(WebhookHandler.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WebhookController(verifier, webhookHandler))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 본문 원문과 서명 헤더를 그대로 검증기에 넘기고, 검증된 웹훅을 핸들러에 전달하는지 검증한다.
     */
    @Test
    void passesRawBodyAndHeadersToVerifierAndHandler() throws Exception {
        PortOneWebhook webhook = new PortOneWebhook("Transaction.Paid", "payment-1");
        when(verifier.verify(BODY, "webhook-1", "v1,sig", "1760000000")).thenReturn(webhook);

        mvc.perform(post("/api/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "webhook-1")
                        .header("webhook-signature", "v1,sig")
                        .header("webhook-timestamp", "1760000000")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookHandler).handle("webhook-1", webhook, BODY);
    }

    /**
     * 서명 헤더가 없거나 서명이 틀려 검증에 실패하면 401(WEBHOOK_001)로 응답하고 핸들러를 호출하지 않는지 검증한다.
     */
    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        when(verifier.verify(any(), isNull(), isNull(), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE));

        mvc.perform(post("/api/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_WEBHOOK_SIGNATURE.getCode()));

        verifyNoInteractions(webhookHandler);
    }
}

package org.example.takecareofmyfridgebe.domain.payment.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPaymentRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPrepareRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 인증 여부, 요청 검증, 토큰의 회원 ID 전달, 비즈니스 예외 응답 변환을 확인한다.
 */
@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, PaymentControllerTest.TestConfig.class})
class PaymentControllerTest {

    private static final Long MEMBER_ID = 7L;
    private static final String PORTONE_PAYMENT_ID = "payment-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentFacade paymentFacade;

    @BeforeEach
    void resetMock() {
        reset(paymentFacade);
    }

    @Test
    @DisplayName("비로그인 상태로 결제 확정을 요청하면 Facade를 호출하지 않고 401(A003)을 반환한다")
    void confirm_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(post("/api/payments/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("1", "\"" + PORTONE_PAYMENT_ID + "\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 확정을 요청하면 토큰의 회원 ID와 요청 본문을 Facade로 넘기고 응답 DTO를 규격대로 직렬화해 반환한다")
    void confirm_success() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(1L, PORTONE_PAYMENT_ID);
        PaymentConfirmResponse response = new PaymentConfirmResponse(
                10L, 1L, PORTONE_PAYMENT_ID, 15000L, PaymentStatus.PAID,
                LocalDateTime.of(2026, 10, 20, 10, 0), PgProvider.KAKAOPAY);
        when(paymentFacade.confirmPayment(MEMBER_ID, request)).thenReturn(response);

        mvc.perform(post("/api/payments/confirm")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("1", "\"" + PORTONE_PAYMENT_ID + "\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.orderId").value(1))
                .andExpect(jsonPath("$.data.portonePaymentId").value(PORTONE_PAYMENT_ID))
                .andExpect(jsonPath("$.data.amount").value(15000))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").value("2026-10-20T10:00:00"));

        verify(paymentFacade).confirmPayment(MEMBER_ID, request);
    }

    @Test
    @DisplayName("orderId가 없거나 portonePaymentId가 공백이면 Facade를 호출하지 않고 400(C001)을 반환한다")
    void confirm_returnBadRequest_whenInvalidBody() throws Exception {
        for (String body : List.of(
                confirmBody("null", "\"" + PORTONE_PAYMENT_ID + "\""),
                confirmBody("1", "\" \""))) {
            mvc.perform(post("/api/payments/confirm")
                            .with(csrf())
                            .with(loginUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 확정 중 금액 불일치 예외가 나면 해당 ErrorCode의 상태와 코드로 응답한다")
    void confirm_returnBadRequest_whenAmountMismatch() throws Exception {
        when(paymentFacade.confirmPayment(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        mvc.perform(post("/api/payments/confirm")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("1", "\"" + PORTONE_PAYMENT_ID + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PAYMENT_AMOUNT_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("비로그인 상태로 결제 실패 처리를 요청하면 Facade를 호출하지 않고 401(A003)을 반환한다")
    void fail_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(post("/api/payments/{portonePaymentId}/fail", PORTONE_PAYMENT_ID)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 실패 처리를 요청하면 토큰의 회원 ID와 경로의 결제 ID로 Facade를 호출하고 빈 성공 응답을 반환한다")
    void fail_success() throws Exception {
        mvc.perform(post("/api/payments/{portonePaymentId}/fail", PORTONE_PAYMENT_ID)
                        .with(csrf())
                        .with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(paymentFacade).failByUser(MEMBER_ID, PORTONE_PAYMENT_ID);
    }

    @Test
    @DisplayName("이미 처리된 결제를 실패 처리하려 하면 409(PAYMENT_005)로 응답한다")
    void fail_returnConflict_whenAlreadyProcessed() throws Exception {
        doThrow(new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT))
                .when(paymentFacade).failByUser(eq(MEMBER_ID), eq(PORTONE_PAYMENT_ID));

        mvc.perform(post("/api/payments/{portonePaymentId}/fail", PORTONE_PAYMENT_ID)
                        .with(csrf())
                        .with(loginUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.ALREADY_PROCESSED_PAYMENT.getCode()));
    }

    @Test
    @DisplayName("비로그인 상태로 결제 준비를 요청하면 Facade를 호출하지 않고 401(A003)을 반환한다")
    void prepare_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(post("/api/orders/{orderId}/payments", 1L)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 준비를 요청하면 토큰의 회원 ID, 경로의 주문 ID, 고른 결제대행사로 Facade를 호출하고 결제창 정보를 규격대로 직렬화해 반환한다")
    void prepare_success() throws Exception {
        PaymentPrepareResponse response = new PaymentPrepareResponse(
                1L, PORTONE_PAYMENT_ID, "대파 외 1건", 15000L, LocalDateTime.of(2026, 10, 20, 10, 10),
                PgProvider.KAKAOPAY, "channel-kakaopay", "EASY_PAY", "KAKAOPAY");
        when(paymentFacade.preparePayment(MEMBER_ID, 1L, new PaymentPrepareRequest(PgProvider.KAKAOPAY)))
                .thenReturn(response);

        mvc.perform(post("/api/orders/{orderId}/payments", 1L)
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prepareBody("\"KAKAOPAY\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(1))
                .andExpect(jsonPath("$.data.portonePaymentId").value(PORTONE_PAYMENT_ID))
                .andExpect(jsonPath("$.data.orderName").value("대파 외 1건"))
                .andExpect(jsonPath("$.data.amount").value(15000))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-10-20T10:10:00"))
                .andExpect(jsonPath("$.data.pgProvider").value("KAKAOPAY"))
                .andExpect(jsonPath("$.data.channelKey").value("channel-kakaopay"))
                .andExpect(jsonPath("$.data.payMethod").value("EASY_PAY"))
                .andExpect(jsonPath("$.data.easyPayProvider").value("KAKAOPAY"));
    }

    @Test
    @DisplayName("결제대행사를 고르지 않으면 Facade를 호출하지 않고 400(C001)으로 응답한다")
    void prepare_returnBadRequest_whenPgProviderMissing() throws Exception {
        mvc.perform(post("/api/orders/{orderId}/payments", 1L)
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prepareBody("null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("이미 결제됐거나 취소된 주문이면 409(PAYMENT_007)로 응답한다")
    void prepare_returnConflict_whenOrderNotPayable() throws Exception {
        when(paymentFacade.preparePayment(MEMBER_ID, 1L, new PaymentPrepareRequest(PgProvider.KAKAOPAY)))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE));

        mvc.perform(post("/api/orders/{orderId}/payments", 1L)
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prepareBody("\"KAKAOPAY\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE.getCode()));
    }

    @Test
    @DisplayName("등록 카드로 결제하면 토큰의 회원 ID, 경로의 주문 ID, 고른 카드로 Facade를 호출하고 결제 확정 응답을 반환한다")
    void payWithBillingKey_success() throws Exception {
        PaymentConfirmResponse response = new PaymentConfirmResponse(10L, 1L, PORTONE_PAYMENT_ID, 15000L,
                PaymentStatus.PAID, LocalDateTime.of(2026, 11, 4, 10, 0), PgProvider.TOSSPAYMENTS);
        when(paymentFacade.payWithBillingKey(MEMBER_ID, 1L, new BillingKeyPaymentRequest(7L, "271828"))).thenReturn(response);

        mvc.perform(post("/api/orders/1/payments/billing-key")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":7,\"pin\":\"271828\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.pgProvider").value("TOSSPAYMENTS"));
    }

    @Test
    @DisplayName("결제할 카드를 고르지 않으면 Facade를 호출하지 않고 400(C001)을 반환한다")
    void payWithBillingKey_returnBadRequest_whenCardIdMissing() throws Exception {
        mvc.perform(post("/api/orders/1/payments/billing-key")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("카드가 거절되면 409(PAYMENT_010)로 응답한다")
    void payWithBillingKey_returnConflict_whenRejected() throws Exception {
        when(paymentFacade.payWithBillingKey(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.BILLING_PAYMENT_REJECTED));

        mvc.perform(post("/api/orders/1/payments/billing-key")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":7,\"pin\":\"271828\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.BILLING_PAYMENT_REJECTED.getCode()));
    }

    @Test
    @DisplayName("결제 비밀번호가 숫자 6자리가 아니면 Facade를 호출하지 않고 400(C001)을 반환한다")
    void payWithBillingKey_returnBadRequest_whenPinInvalid() throws Exception {
        mvc.perform(post("/api/orders/1/payments/billing-key")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":7,\"pin\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    private static RequestPostProcessor loginUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(MEMBER_ID, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static String confirmBody(String orderId, String portonePaymentId) {
        return """
                {
                  "orderId": %s,
                  "portonePaymentId": %s
                }
                """.formatted(orderId, portonePaymentId);
    }

    private static String prepareBody(String pgProvider) {
        return """
                {
                  "pgProvider": %s
                }
                """.formatted(pgProvider);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        PaymentFacade paymentFacade() {
            return mock(PaymentFacade.class);
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}

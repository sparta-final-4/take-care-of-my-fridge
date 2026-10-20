package org.example.takecareofmyfridgebe.domain.payment.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
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

    /**
     * 비로그인 상태로 결제 확정을 요청하면 Facade를 호출하지 않고 401(A003)을 반환하는지 검증한다.
     */
    @Test
    void confirmWithoutLoginReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/payments/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("1", "\"" + PORTONE_PAYMENT_ID + "\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    /**
     * 결제 확정 요청 시 토큰의 회원 ID와 요청 본문이 Facade로 전달되고,
     * 응답 DTO가 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void confirmPassesTokenMemberIdAndReturnsResponse() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(1L, PORTONE_PAYMENT_ID);
        PaymentConfirmResponse response = new PaymentConfirmResponse(
                10L, 1L, PORTONE_PAYMENT_ID, 15000L, PaymentStatus.PAID,
                LocalDateTime.of(2026, 10, 20, 10, 0));
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

    /**
     * orderId 누락 또는 portonePaymentId 공백이면 Facade를 호출하지 않고 400(C001)을 반환하는지 검증한다.
     */
    @Test
    void confirmWithInvalidBodyReturnsBadRequest() throws Exception {
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

    /**
     * 결제 확정 중 금액 불일치 예외가 발생하면 해당 ErrorCode의 상태와 코드로 응답하는지 검증한다.
     */
    @Test
    void confirmAmountMismatchReturnsErrorCode() throws Exception {
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

    /**
     * 비로그인 상태로 결제 실패 처리를 요청하면 Facade를 호출하지 않고 401(A003)을 반환하는지 검증한다.
     */
    @Test
    void failWithoutLoginReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/payments/{portonePaymentId}/fail", PORTONE_PAYMENT_ID)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(paymentFacade);
    }

    /**
     * 결제 실패 처리 요청 시 토큰의 회원 ID와 경로의 결제 ID로 Facade를 호출하고 빈 성공 응답을 반환하는지 검증한다.
     */
    @Test
    void failPassesTokenMemberIdAndPaymentId() throws Exception {
        mvc.perform(post("/api/payments/{portonePaymentId}/fail", PORTONE_PAYMENT_ID)
                        .with(csrf())
                        .with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(paymentFacade).failByUser(MEMBER_ID, PORTONE_PAYMENT_ID);
    }

    /**
     * 이미 처리된 결제를 실패 처리하려 하면 409(PAYMENT_005)로 응답하는지 검증한다.
     */
    @Test
    void failAlreadyProcessedReturnsConflict() throws Exception {
        doThrow(new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT))
                .when(paymentFacade).failByUser(eq(MEMBER_ID), eq(PORTONE_PAYMENT_ID));

        mvc.perform(post("/api/payments/{portonePaymentId}/fail", PORTONE_PAYMENT_ID)
                        .with(csrf())
                        .with(loginUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.ALREADY_PROCESSED_PAYMENT.getCode()));
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

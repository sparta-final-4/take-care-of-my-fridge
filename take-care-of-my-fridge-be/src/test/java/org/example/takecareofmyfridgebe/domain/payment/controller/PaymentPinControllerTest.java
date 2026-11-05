package org.example.takecareofmyfridgebe.domain.payment.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinChangeRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinResetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinSetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinStatusResponse;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentPinFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinService;
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

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentPinController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 인증 여부, 요청 검증, 토큰의 회원 ID 전달, 응답 형식을 확인한다.
 */
@WebMvcTest(PaymentPinController.class)
@Import({SecurityConfig.class, PaymentPinControllerTest.TestConfig.class})
class PaymentPinControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentPinService paymentPinService;

    @Autowired
    private PaymentPinFacade paymentPinFacade;

    @BeforeEach
    void resetMock() {
        reset(paymentPinService, paymentPinFacade);
    }

    @Test
    @DisplayName("비로그인 상태로 결제 비밀번호 상태를 조회하면 서비스를 호출하지 않고 401(A003)을 반환한다")
    void getStatus_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(get("/api/payment-pin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(paymentPinService);
    }

    @Test
    @DisplayName("결제 비밀번호 상태를 조회하면 설정 여부·잠김·남은 횟수를 반환한다")
    void getStatus_success() throws Exception {
        when(paymentPinService.getStatus(MEMBER_ID)).thenReturn(new PaymentPinStatusResponse(true, false, 3));

        mvc.perform(get("/api/payment-pin").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isSet").value(true))
                .andExpect(jsonPath("$.data.isLocked").value(false))
                .andExpect(jsonPath("$.data.remainingAttempts").value(3));
    }

    @Test
    @DisplayName("결제 비밀번호를 설정하면 토큰의 회원 ID로 서비스를 호출하고 201을 반환한다")
    void set_returnCreated() throws Exception {
        mvc.perform(post("/api/payment-pin")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"271828\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentPinService).set(MEMBER_ID, new PaymentPinSetRequest("271828"));
    }

    @Test
    @DisplayName("결제 비밀번호가 숫자 6자리가 아니면 서비스를 호출하지 않고 400(C001)을 반환한다")
    void set_returnBadRequest_whenNotSixDigits() throws Exception {
        mvc.perform(post("/api/payment-pin")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"12a45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(paymentPinService);
    }

    @Test
    @DisplayName("쉬운 비밀번호면 400(PIN_004)으로 응답한다")
    void set_returnBadRequest_whenWeak() throws Exception {
        doThrow(new BusinessException(ErrorCode.PAYMENT_PIN_WEAK)).when(paymentPinService).set(MEMBER_ID, new PaymentPinSetRequest("111111"));

        mvc.perform(post("/api/payment-pin")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"111111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PAYMENT_PIN_WEAK.getCode()));
    }

    @Test
    @DisplayName("결제 비밀번호를 바꾸면 현재·새 비밀번호를 서비스로 넘긴다")
    void change_success() throws Exception {
        mvc.perform(patch("/api/payment-pin")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"271828\",\"newPin\":\"314159\"}"))
                .andExpect(status().isOk());

        verify(paymentPinService).change(MEMBER_ID, new PaymentPinChangeRequest("271828", "314159"));
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400(PIN_003)과 남은 횟수가 담긴 메시지로 응답한다")
    void change_returnBadRequest_whenMismatch() throws Exception {
        doThrow(new BusinessException(ErrorCode.PAYMENT_PIN_MISMATCH, "결제 비밀번호가 맞지 않아요. (남은 횟수 4번)"))
                .when(paymentPinService).change(MEMBER_ID, new PaymentPinChangeRequest("000123", "314159"));

        mvc.perform(patch("/api/payment-pin")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"000123\",\"newPin\":\"314159\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PAYMENT_PIN_MISMATCH.getCode()))
                .andExpect(jsonPath("$.error.message").value("결제 비밀번호가 맞지 않아요. (남은 횟수 4번)"));
    }

    @Test
    @DisplayName("로그인 비밀번호로 재설정하면 로그인 비밀번호와 새 비밀번호를 서비스로 넘긴다")
    void reset_success() throws Exception {
        mvc.perform(post("/api/payment-pin/reset")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginPassword\":\"login-password-1!\",\"newPin\":\"314159\"}"))
                .andExpect(status().isOk());

        verify(paymentPinFacade).reset(MEMBER_ID, new PaymentPinResetRequest("login-password-1!", "314159"));
    }

    @Test
    @DisplayName("로그인 비밀번호가 비어 있으면 서비스를 호출하지 않고 400(C001)을 반환한다")
    void reset_returnBadRequest_whenLoginPasswordBlank() throws Exception {
        mvc.perform(post("/api/payment-pin/reset")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginPassword\":\" \",\"newPin\":\"314159\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(paymentPinFacade);
    }

    @Test
    @DisplayName("재설정에서 로그인 비밀번호를 5번 넘게 틀렸으면 429(PIN_007)로 응답한다")
    void reset_returnTooManyRequests_whenLimited() throws Exception {
        doThrow(new BusinessException(ErrorCode.PAYMENT_PIN_RESET_LIMITED)).when(paymentPinFacade)
                .reset(MEMBER_ID, new PaymentPinResetRequest("login-password-1!", "314159"));

        mvc.perform(post("/api/payment-pin/reset")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginPassword\":\"login-password-1!\",\"newPin\":\"314159\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PAYMENT_PIN_RESET_LIMITED.getCode()));
    }

    private static RequestPostProcessor loginUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(MEMBER_ID, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        PaymentPinService paymentPinService() {
            return mock(PaymentPinService.class);
        }

        @Bean
        PaymentPinFacade paymentPinFacade() {
            return mock(PaymentPinFacade.class);
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

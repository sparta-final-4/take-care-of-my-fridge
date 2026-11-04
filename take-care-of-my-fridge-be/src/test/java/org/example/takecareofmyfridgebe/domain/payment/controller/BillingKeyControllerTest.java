package org.example.takecareofmyfridgebe.domain.payment.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyRegisterRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.facade.BillingKeyFacade;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BillingKeyController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 인증 여부, 요청 검증, 토큰의 회원 ID 전달, 응답 형식을 확인한다.
 */
@WebMvcTest(BillingKeyController.class)
@Import({SecurityConfig.class, BillingKeyControllerTest.TestConfig.class})
class BillingKeyControllerTest {

    private static final Long MEMBER_ID = 7L;
    private static final BillingKeyResponse CARD = new BillingKeyResponse(
            1L, "신한카드 Deep Dream", "1234", CardType.CREDIT, true, LocalDateTime.of(2026, 11, 4, 10, 0));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BillingKeyFacade billingKeyFacade;

    @BeforeEach
    void resetMock() {
        reset(billingKeyFacade);
    }

    @Test
    @DisplayName("비로그인 상태로 카드 등록을 준비하면 Facade를 호출하지 않고 401(A003)을 반환한다")
    void prepare_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(post("/api/billing-keys/prepare").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(billingKeyFacade);
    }

    @Test
    @DisplayName("카드 등록을 준비하면 토큰의 회원 ID로 Facade를 호출하고 카드 등록 창 값을 반환한다")
    void prepare_success() throws Exception {
        when(billingKeyFacade.prepare(MEMBER_ID)).thenReturn(
                new BillingKeyPrepareResponse("channel-billing", "billing-abc", "카드 등록", "member-7"));

        mvc.perform(post("/api/billing-keys/prepare").with(csrf()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelKey").value("channel-billing"))
                .andExpect(jsonPath("$.data.issueId").value("billing-abc"))
                .andExpect(jsonPath("$.data.issueName").value("카드 등록"))
                .andExpect(jsonPath("$.data.customerId").value("member-7"));
    }

    @Test
    @DisplayName("카드가 5장이면 409(BILLING_002)로 응답한다")
    void prepare_returnConflict_whenLimitExceeded() throws Exception {
        when(billingKeyFacade.prepare(MEMBER_ID))
                .thenThrow(new BusinessException(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED));

        mvc.perform(post("/api/billing-keys/prepare").with(csrf()).with(loginUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED.getCode()));
    }

    @Test
    @DisplayName("카드 등록을 확정하면 토큰의 회원 ID와 빌링키를 Facade로 넘기고 201과 카드 정보를 반환하며 빌링키 값은 내려주지 않는다")
    void register_returnCreated() throws Exception {
        when(billingKeyFacade.register(MEMBER_ID, new BillingKeyRegisterRequest("billing-key-1"))).thenReturn(CARD);

        mvc.perform(post("/api/billing-keys")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingKey\":\"billing-key-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cardId").value(1))
                .andExpect(jsonPath("$.data.cardName").value("신한카드 Deep Dream"))
                .andExpect(jsonPath("$.data.cardLast4").value("1234"))
                .andExpect(jsonPath("$.data.cardType").value("CREDIT"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.data.billingKey").doesNotExist());

        verify(billingKeyFacade).register(MEMBER_ID, new BillingKeyRegisterRequest("billing-key-1"));
    }

    @Test
    @DisplayName("빌링키가 비어 있으면 Facade를 호출하지 않고 400(C001)을 반환한다")
    void register_returnBadRequest_whenBillingKeyBlank() throws Exception {
        mvc.perform(post("/api/billing-keys")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingKey\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(billingKeyFacade);
    }

    @Test
    @DisplayName("빌링키를 확인하지 못하면 409(BILLING_003)로 응답한다")
    void register_returnConflict_whenNotVerified() throws Exception {
        when(billingKeyFacade.register(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.BILLING_KEY_NOT_VERIFIED));

        mvc.perform(post("/api/billing-keys")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingKey\":\"billing-key-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.BILLING_KEY_NOT_VERIFIED.getCode()));
    }

    @Test
    @DisplayName("카드 목록을 조회하면 토큰의 회원 ID로 Facade를 호출하고 카드 목록을 반환한다")
    void getCards_success() throws Exception {
        when(billingKeyFacade.getCards(MEMBER_ID)).thenReturn(List.of(CARD));

        mvc.perform(get("/api/billing-keys").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cardId").value(1))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("비로그인 상태로 카드 목록을 조회하면 401(A003)을 반환한다")
    void getCards_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(get("/api/billing-keys"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(billingKeyFacade);
    }

    @Test
    @DisplayName("대표 카드를 지정하면 토큰의 회원 ID와 경로의 카드 ID로 Facade를 호출하고 바뀐 카드 목록을 반환한다")
    void setDefault_success() throws Exception {
        when(billingKeyFacade.setDefault(MEMBER_ID, 1L)).thenReturn(List.of(CARD));

        mvc.perform(patch("/api/billing-keys/1/default").with(csrf()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cardId").value(1))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("내 카드가 아니면 대표 지정을 404(BILLING_004)로 응답한다")
    void setDefault_returnNotFound_whenNotOwnCard() throws Exception {
        when(billingKeyFacade.setDefault(MEMBER_ID, 9L))
                .thenThrow(new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND));

        mvc.perform(patch("/api/billing-keys/9/default").with(csrf()).with(loginUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.BILLING_KEY_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("카드를 삭제하면 토큰의 회원 ID와 경로의 카드 ID로 Facade를 호출하고 빈 성공 응답을 반환한다")
    void delete_success() throws Exception {
        mvc.perform(delete("/api/billing-keys/1").with(csrf()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(billingKeyFacade).delete(MEMBER_ID, 1L);
    }

    @Test
    @DisplayName("비로그인 상태로 카드를 삭제하면 Facade를 호출하지 않고 401을 반환한다")
    void delete_returnUnauthorized_whenNotLoggedIn() throws Exception {
        mvc.perform(delete("/api/billing-keys/1").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(billingKeyFacade);
    }

    @Test
    @DisplayName("내 카드가 아니면 삭제를 404(BILLING_004)로 응답한다")
    void delete_returnNotFound_whenNotOwnCard() throws Exception {
        doThrow(new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND)).when(billingKeyFacade).delete(MEMBER_ID, 9L);

        mvc.perform(delete("/api/billing-keys/9").with(csrf()).with(loginUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.BILLING_KEY_NOT_FOUND.getCode()));
    }

    private static RequestPostProcessor loginUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(MEMBER_ID, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        BillingKeyFacade billingKeyFacade() {
            return mock(BillingKeyFacade.class);
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

package org.example.takecareofmyfridgebe.domain.auth.controller;

import jakarta.servlet.http.Cookie;
import org.example.takecareofmyfridgebe.domain.auth.dto.LoginMember;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.auth.service.LoginService;
import org.example.takecareofmyfridgebe.domain.auth.service.RefreshService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthCookies.class, AuthControllerTest.TestConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LoginService loginService;

    @Autowired
    private RefreshService refreshService;

    @BeforeEach
    void resetMock() {
        reset(loginService, refreshService);
    }

    @Test
    void anonymousLoginSetsHttpOnlyCookiesAndReturnsMember() throws Exception {
        LoginMember member = new LoginMember(4L, "cook@example.com", "요리왕",
                Member.Role.USER, Member.Status.ACTIVE, List.of(), null);
        when(loginService.login("cook@example.com", "password"))
                .thenReturn(new LoginService.LoginResult(member, "access", "refresh"));

        List<String> cookies = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cook@example.com","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(4))
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        assertEquals(2, cookies.size());
        assertTrue(cookies.get(0).contains("access_token=access"));
        assertTrue(cookies.get(1).contains("refresh_token=refresh"));
        cookies.forEach(cookie -> {
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("SameSite=Lax"));
        });
    }

    @Test
    void invalidEmailIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bad","password":"password"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(loginService);
    }

    @Test
    void credentialAndSuspensionErrorsKeepTheirStatus() throws Exception {
        when(loginService.login("cook@example.com", "wrong"))
                .thenThrow(new BusinessException(ErrorCode.LOGIN_FAILED));
        when(loginService.login("cook@example.com", "password"))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cook@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cook@example.com","password":"password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void refreshRotatesBothCookiesWithRemainingLifetime() throws Exception {
        when(refreshService.refresh("old"))
                .thenReturn(new RefreshService.RefreshedTokens("new-access", "new-refresh", Duration.ofDays(6)));

        List<String> cookies = mvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new Cookie("refresh_token", "old")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        assertEquals(2, cookies.size());
        assertTrue(cookies.get(0).contains("access_token=new-access"));
        assertTrue(cookies.get(1).contains("refresh_token=new-refresh"));
        assertTrue(cookies.get(1).contains("Max-Age=518400"));
    }

    @Test
    void refreshRequiresCsrfAndRefreshCookie() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "old")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_004"));

        verifyNoInteractions(refreshService);
    }

    @Test
    void invalidRefreshTokenReturnsUnauthorized() throws Exception {
        when(refreshService.refresh("invalid"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        mvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new Cookie("refresh_token", "invalid")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        LoginService loginService() {
            return mock(LoginService.class);
        }

        @Bean
        RefreshService refreshService() {
            return mock(RefreshService.class);
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

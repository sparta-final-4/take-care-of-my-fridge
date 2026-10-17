package org.example.takecareofmyfridgebe.domain.member.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.dto.EmailAvailabilityResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.NicknameAvailabilityResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, MemberControllerTest.TestConfig.class})
class MemberControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemberService memberService;

    @BeforeEach
    void resetMock() {
        reset(memberService);
    }

    @Test
    void anonymousUserCanSignUpWithoutCsrfToken() throws Exception {
        when(memberService.signUp(any())).thenReturn(new SignUpResponse(10L));

        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cook@example.com","password":"abcdefgh12!","nickname":"요리왕","tastes":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").value(10));
    }

    @Test
    void invalidPasswordIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cook@example.com","password":"ab1!","nickname":"요리왕","tastes":[]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(memberService.signUp(any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cook@example.com","password":"abcdefgh12!","nickname":"요리왕","tastes":[]}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void otherPostStillRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/members/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedRequestWithoutAccessCookieReturnsJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A003"));
    }

    @Test
    void anonymousUserCanCheckEmailAvailability() throws Exception {
        when(memberService.checkEmailAvailability("cook@example.com"))
                .thenReturn(new EmailAvailabilityResponse(false));

        mvc.perform(get("/api/members/email-availability").param("email", "cook@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void invalidEmailFormatIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(get("/api/members/email-availability").param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(memberService);
    }

    @Test
    void missingEmailIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(get("/api/members/email-availability"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @Test
    void emailLongerThan255IsRejectedBeforeCallingService() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";

        mvc.perform(get("/api/members/email-availability").param("email", email))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @Test
    void anonymousUserCanCheckNicknameWithStrippedValue() throws Exception {
        when(memberService.checkNicknameAvailability("요리왕", null))
                .thenReturn(new NicknameAvailabilityResponse(false));

        mvc.perform(get("/api/members/nickname-availability").param("nickname", "  요리왕 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void loggedInMemberIdIsPassedToNicknameCheck() throws Exception {
        when(memberService.checkNicknameAvailability("요리왕", 7L))
                .thenReturn(new NicknameAvailabilityResponse(true));

        mvc.perform(get("/api/members/nickname-availability").param("nickname", "요리왕")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void blankNicknameIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(get("/api/members/nickname-availability").param("nickname", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(memberService);
    }

    @Test
    void missingNicknameIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(get("/api/members/nickname-availability"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @Test
    void nicknameLongerThan30AfterStripIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(get("/api/members/nickname-availability").param("nickname", " " + "가".repeat(31) + " "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        MemberService memberService() {
            return mock(MemberService.class);
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

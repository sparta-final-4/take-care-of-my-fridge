package org.example.takecareofmyfridgebe.domain.member.controller;

import org.example.takecareofmyfridgebe.domain.member.dto.SignUpResponse;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @TestConfiguration
    static class TestConfig {

        @Bean
        MemberService memberService() {
            return mock(MemberService.class);
        }
    }
}

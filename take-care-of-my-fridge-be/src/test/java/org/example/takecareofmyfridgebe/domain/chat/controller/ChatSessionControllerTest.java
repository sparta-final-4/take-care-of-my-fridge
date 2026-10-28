package org.example.takecareofmyfridgebe.domain.chat.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatSessionController.class)
@Import({SecurityConfig.class, ChatSessionControllerTest.TestConfig.class, ChatSessionController.class})
class ChatSessionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    @DisplayName("비로그인 사용자가 세션 시작 요청 시 401 Unauthorized를 반환한다")
    void startSessionAnonymousReturns401() throws Exception {
        mvc.perform(post("/api/chat-sessions")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("A003"));
    }

    @Test
    @DisplayName("로그인 사용자가 세션 시작 요청 시 200 OK와 세션 응답을 반환한다")
    void startSessionReturns200WithSession() throws Exception {
        Long memberId = 1L;
        ChatSessionResponse dummyResponse = new ChatSessionResponse(
                10L,
                ChatSessionStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 28, 12, 0, 0),
                LocalDateTime.of(2026, 10, 28, 12, 0, 0)
        );
        when(chatSessionService.getOrCreateActiveSession(memberId)).thenReturn(dummyResponse);

        mvc.perform(post("/api/chat-sessions")
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("로그인 사용자가 세션 단건 조회 시 200 OK와 세션 정보를 반환한다")
    void getSessionReturns200WithSession() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSessionResponse dummyResponse = new ChatSessionResponse(
                sessionId,
                ChatSessionStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 28, 12, 0, 0),
                LocalDateTime.of(2026, 10, 28, 12, 0, 0)
        );
        when(chatSessionService.getSession(memberId, sessionId)).thenReturn(dummyResponse);

        mvc.perform(get("/api/chat-sessions/{sessionId}", sessionId)
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("존재하지 않거나 타인의 세션 조회 시 404 Not Found (CHAT_001)를 반환한다")
    void getSessionReturns404WhenNotFound() throws Exception {
        Long memberId = 1L;
        Long sessionId = 999L;
        when(chatSessionService.getSession(memberId, sessionId))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        mvc.perform(get("/api/chat-sessions/{sessionId}", sessionId)
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_001"));
    }

    @Test
    @DisplayName("로그인 사용자가 세션 종료 요청 시 200 OK와 종료된 세션 정보를 반환한다")
    void completeSessionReturns200WithCompletedSession() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSessionResponse dummyResponse = new ChatSessionResponse(
                sessionId,
                ChatSessionStatus.COMPLETED,
                LocalDateTime.of(2026, 10, 28, 12, 0, 0),
                LocalDateTime.of(2026, 10, 28, 12, 0, 0)
        );
        when(chatSessionService.completeSession(memberId, sessionId)).thenReturn(dummyResponse);

        mvc.perform(patch("/api/chat-sessions/{sessionId}/complete", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("이미 종료된 세션을 종료 요청 시 409 Conflict (CHAT_005)를 반환한다")
    void completeSessionReturns409WhenAlreadyCompleted() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        when(chatSessionService.completeSession(memberId, sessionId))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED));

        mvc.perform(patch("/api/chat-sessions/{sessionId}/complete", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_005"));
    }

    @Test
    @DisplayName("존재하지 않거나 타인의 세션을 종료 요청 시 404 Not Found (CHAT_001)를 반환한다")
    void completeSessionReturns404WhenNotFound() throws Exception {
        Long memberId = 1L;
        Long sessionId = 999L;
        when(chatSessionService.completeSession(memberId, sessionId))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        mvc.perform(patch("/api/chat-sessions/{sessionId}/complete", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_001"));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(memberId, 1L, Member.Role.USER),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }

        @Bean
        ChatSessionService chatSessionService() {
            return mock(ChatSessionService.class);
        }
    }
}

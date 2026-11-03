package org.example.takecareofmyfridgebe.domain.chat.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListStatus;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionStatusCounts;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    void returnsUnauthorized_whenStartingWithoutLogin() throws Exception {
        mvc.perform(post("/api/chat-sessions")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("A003"));
    }

    @Test
    @DisplayName("로그인 사용자가 세션 시작 요청 시 200 OK와 세션 응답을 반환한다")
    void returnsSession_whenStarting() throws Exception {
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
    void returnsOwnSession() throws Exception {
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
    void returnsNotFound_whenGettingOthersOrMissingSession() throws Exception {
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
    void returnsCompletedSession_whenCompleting() throws Exception {
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
    void returnsConflict_whenSessionIsAlreadyCompleted() throws Exception {
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
    void returnsNotFound_whenCompletingOthersOrMissingSession() throws Exception {
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

    @Test
    @DisplayName("비로그인 사용자가 상담 목록을 조회하면 401을 반환한다")
    void returnsUnauthorized_whenListingWithoutLogin() throws Exception {
        mvc.perform(get("/api/chat-sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("A003"));
    }

    @Test
    @DisplayName("상담 목록은 기본 page=1, size=10, status=ALL로 조회하고 탭 건수와 공통 페이지 형식으로 반환한다")
    void returnsSessionPageWithDefaults() throws Exception {
        Long memberId = 1L;
        when(chatSessionService.getMySessions(eq(memberId),
                argThat(req -> req.page() == 1 && req.size() == 10 && req.status() == ChatSessionListStatus.ALL)))
                .thenReturn(listResponse());

        mvc.perform(get("/api/chat-sessions")
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.total").value(3))
                .andExpect(jsonPath("$.data.counts.active").value(1))
                .andExpect(jsonPath("$.data.counts.ended").value(2))
                .andExpect(jsonPath("$.data.sessions.content[0].sessionId").value(10L))
                .andExpect(jsonPath("$.data.sessions.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.sessions.content[0].startedAt").value("2026-10-28 12:00:00"))
                .andExpect(jsonPath("$.data.sessions.content[0].lastMessageAt").value("2026-10-28 12:05:00"))
                .andExpect(jsonPath("$.data.sessions.content[0].messageCount").value(4))
                .andExpect(jsonPath("$.data.sessions.content[0].firstQuestion").value("감자로 뭐 만들까요?"))
                .andExpect(jsonPath("$.data.sessions.content[0].lastMessagePreview").value("감자전을 추천해요"))
                .andExpect(jsonPath("$.data.sessions.pageNumber").value(1))
                .andExpect(jsonPath("$.data.sessions.totalElements").value(1))
                .andExpect(jsonPath("$.data.sessions.isLast").value(true));
    }

    @Test
    @DisplayName("상담 목록 status 파라미터를 상태 필터로 받는다")
    void passesStatusFilter_whenStatusIsGiven() throws Exception {
        Long memberId = 1L;
        when(chatSessionService.getMySessions(eq(memberId), argThat(req -> req.status() == ChatSessionListStatus.ENDED)))
                .thenReturn(listResponse());

        mvc.perform(get("/api/chat-sessions")
                        .param("status", "ENDED")
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions.content[0].sessionId").value(10L));
    }

    @Test
    @DisplayName("상담 목록 status가 ALL/ACTIVE/ENDED가 아니면 400을 반환한다")
    void returnsBadRequest_whenStatusIsUnknown() throws Exception {
        mvc.perform(get("/api/chat-sessions")
                        .param("status", "COMPLETED")
                        .with(authentication(authenticationFor(1L))))
                .andExpect(status().isBadRequest());
    }

    private ChatSessionListResponse listResponse() {
        ChatSessionSummaryResponse summary = new ChatSessionSummaryResponse(
                10L,
                ChatSessionStatus.COMPLETED,
                LocalDateTime.of(2026, 10, 28, 12, 0, 0),
                LocalDateTime.of(2026, 10, 28, 12, 5, 0),
                4,
                "감자로 뭐 만들까요?",
                "감자전을 추천해요"
        );
        return new ChatSessionListResponse(
                new ChatSessionStatusCounts(3, 1, 2),
                PageResponse.of(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1))
        );
    }

    @Test
    @DisplayName("상담 목록 page가 1보다 작으면 400을 반환한다")
    void returnsBadRequest_whenPageIsLessThanOne() throws Exception {
        mvc.perform(get("/api/chat-sessions")
                        .param("page", "0")
                        .with(authentication(authenticationFor(1L))))
                .andExpect(status().isBadRequest());
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

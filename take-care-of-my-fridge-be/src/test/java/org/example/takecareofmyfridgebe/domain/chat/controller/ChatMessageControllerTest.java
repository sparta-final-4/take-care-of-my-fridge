package org.example.takecareofmyfridgebe.domain.chat.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatReplyGenerator;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatMessageController.class)
@Import({SecurityConfig.class, ChatMessageControllerTest.TestConfig.class, ChatMessageController.class})
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatReplyGenerator chatReplyGenerator;

    @Test
    @DisplayName("비로그인 사용자가 메시지 전송 요청 시 401 Unauthorized를 반환한다")
    void sendMessageAnonymousReturns401() throws Exception {
        mvc.perform(post("/api/chat-sessions/1/messages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안녕\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("A003"));
    }

    @Test
    @DisplayName("로그인 사용자가 정상 메시지 전송 시 200 OK와 [사용자 메시지, 답변] 리스트를 반환한다")
    void sendMessageReturns200WithTwoMessages() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "된장찌개 끓이는 법 알려줘";
        String stubReply = "아직 AI 연결 전이에요. 받은 메시지: " + userContent;

        when(chatReplyGenerator.generate(sessionId, userContent)).thenReturn(stubReply);

        ChatMessageResponse userMsg = new ChatMessageResponse(1L, 1, ChatMessageRole.USER, userContent, LocalDateTime.now());
        ChatMessageResponse assistantMsg = new ChatMessageResponse(2L, 2, ChatMessageRole.ASSISTANT, stubReply, LocalDateTime.now());

        when(chatMessageService.saveExchange(eq(sessionId), eq(userContent), eq(stubReply)))
                .thenReturn(List.of(userMsg, assistantMsg));

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + userContent + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value(userContent))
                .andExpect(jsonPath("$.data[1].sequence").value(2))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].content").value(stubReply));
    }

    @Test
    @DisplayName("메시지 내용이 비어 있으면 400 Bad Request를 반환한다")
    void sendMessageBlankContentReturns400() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("존재하지 않거나 타인의 세션에 메시지 전송 시 404 Not Found (CHAT_001)를 반환한다")
    void sendMessageWhenSessionNotFoundReturns404() throws Exception {
        Long memberId = 1L;
        Long sessionId = 999L;

        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .when(chatMessageService).validateSessionForSending(memberId, sessionId);

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안녕하세요\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_001"));
    }

    @Test
    @DisplayName("이미 종료된 세션에 메시지 전송 시 409 Conflict (CHAT_005)를 반환한다")
    void sendMessageWhenSessionAlreadyCompletedReturns409() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED))
                .when(chatMessageService).validateSessionForSending(memberId, sessionId);

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안녕하세요\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_005"));
    }

    @Test
    @DisplayName("메시지 목록 조회 시 200 OK와 전체 메시지 목록을 반환한다")
    void getMessagesReturns200WithList() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        ChatMessageResponse msg1 = new ChatMessageResponse(1L, 1, ChatMessageRole.USER, "질문 1", LocalDateTime.now());
        ChatMessageResponse msg2 = new ChatMessageResponse(2L, 2, ChatMessageRole.ASSISTANT, "답변 1", LocalDateTime.now());

        when(chatMessageService.getMessages(memberId, sessionId)).thenReturn(List.of(msg1, msg2));

        mvc.perform(get("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(authentication(authenticationFor(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[1].sequence").value(2));
    }

    @Test
    @DisplayName("존재하지 않거나 타인의 세션 메시지 조회 시 404 Not Found를 반환한다")
    void getMessagesWhenSessionNotFoundReturns404() throws Exception {
        Long memberId = 1L;
        Long sessionId = 999L;

        when(chatMessageService.getMessages(memberId, sessionId))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        mvc.perform(get("/api/chat-sessions/{sessionId}/messages", sessionId)
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
        ChatMessageService chatMessageService() {
            return mock(ChatMessageService.class);
        }

        @Bean
        ChatReplyGenerator chatReplyGenerator() {
            return mock(ChatReplyGenerator.class);
        }
    }
}

package org.example.takecareofmyfridgebe.domain.chat.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatStreamService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatMessageController.class)
@Import({SecurityConfig.class, ChatMessageControllerTest.TestConfig.class, ChatMessageController.class})
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatStreamService chatStreamService;

    @Autowired
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        reset(chatStreamService, chatMessageService);
    }

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
    @DisplayName("정상: 컨트롤러가 ChatStreamService를 호출하고 X-Accel-Buffering: no 헤더와 함께 SSE 스트림을 반환한다")
    void sendMessageStreamsSSEWithHeader() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "된장찌개 레시피 알려줘";

        Flux<ServerSentEvent<String>> mockStream = Flux.just(
                ServerSentEvent.<String>builder().event("message").data("{\"text\":\"된장\"}").build(),
                ServerSentEvent.<String>builder().event("done").data("{\"userMessageId\":100,\"assistantMessageId\":101}").build()
        );

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq(userContent)))
                .thenReturn(mockStream);

        MvcResult mvcResult = mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + userContent + "\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();

        mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        String responseContent = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(responseContent).contains("event:message\ndata:{\"text\":\"된장\"}");
        assertThat(responseContent).contains("event:done\ndata:{\"userMessageId\":100,\"assistantMessageId\":101}");

        verify(chatStreamService).streamMessage(memberId, sessionId, userContent);
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
    @DisplayName("메시지 내용이 정확히 500자이면 정상 처리(200 OK SSE)된다")
    void sendMessageContent500CharactersReturns200() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        String content500 = "a".repeat(500);

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq(content500)))
                .thenReturn(Flux.empty());

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + content500 + "\"}"))
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("메시지 내용이 501자 이상이면 400 Bad Request를 반환한다")
    void sendMessageContent501CharactersReturns400() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;
        String content501 = "a".repeat(501);

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + content501 + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("30분 지난 만료된 세션에 메시지 전송 시 409 Conflict (CHAT_006) JSON을 반환한다")
    void sendMessageWhenSessionExpiredReturns409Json() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq("안녕하세요")))
                .thenThrow(new BusinessException(ErrorCode.CHAT_SESSION_EXPIRED));

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안녕하세요\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_006"));
    }

    @Test
    @DisplayName("하루 질문 한도(30회)를 초과한 회원이 메시지 전송 시 429 Too Many Requests (CHAT_007) JSON을 반환한다")
    void sendMessageWhenDailyLimitExceededReturns429Json() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq("안녕하세요")))
                .thenThrow(new BusinessException(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED));

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안녕하세요\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_007"));
    }

    @Test
    @DisplayName("Redis 장애 등으로 상담 서비스 불가 시 503 Service Unavailable (CHAT_008) JSON을 반환한다")
    void sendMessageWhenServiceUnavailableReturns503Json() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq("안녕하세요")))
                .thenThrow(new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE));

        mvc.perform(post("/api/chat-sessions/{sessionId}/messages", sessionId)
                        .with(csrf())
                        .with(authentication(authenticationFor(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안녕하세요\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHAT_008"));
    }

    @Test
    @DisplayName("존재하지 않거나 타인의 세션에 메시지 전송 시 404 Not Found (CHAT_001) JSON을 반환한다")
    void sendMessageWhenSessionNotFoundReturns404Json() throws Exception {
        Long memberId = 1L;
        Long sessionId = 999L;

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq("안녕하세요")))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

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
    @DisplayName("이미 종료된 세션에 메시지 전송 시 409 Conflict (CHAT_005) JSON을 반환한다")
    void sendMessageWhenSessionAlreadyCompletedReturns409Json() throws Exception {
        Long memberId = 1L;
        Long sessionId = 10L;

        when(chatStreamService.streamMessage(eq(memberId), eq(sessionId), eq("안녕하세요")))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED));

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
        ChatStreamService chatStreamService() {
            return mock(ChatStreamService.class);
        }

        @Bean
        ChatMessageService chatMessageService() {
            return mock(ChatMessageService.class);
        }
    }
}

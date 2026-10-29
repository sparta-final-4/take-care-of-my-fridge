package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatStreamServiceTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatDailyLimitService chatDailyLimitService;

    @Mock
    private ChatReplyGenerator chatReplyGenerator;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChatStreamService chatStreamService;

    @Test
    @DisplayName("정상: message 조각들이 방출되고 saveExchange가 boundedElastic 스레드에서 실행된 후 done 이벤트가 온다")
    void streamMessageSavesExchangeOnBoundedElasticScheduler() {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "된장찌개 레시피 알려줘";

        when(chatReplyGenerator.generate(sessionId, userContent))
                .thenReturn(Flux.just("된장", "찌개", " 레시피"));

        AtomicReference<String> executedThreadName = new AtomicReference<>();

        when(chatMessageService.saveExchange(eq(sessionId), eq(userContent), eq("된장찌개 레시피")))
                .thenAnswer(invocation -> {
                    executedThreadName.set(Thread.currentThread().getName());
                    ChatMessageResponse userMsg = new ChatMessageResponse(100L, 1, ChatMessageRole.USER, userContent, LocalDateTime.now());
                    ChatMessageResponse assistantMsg = new ChatMessageResponse(101L, 2, ChatMessageRole.ASSISTANT, "된장찌개 레시피", LocalDateTime.now());
                    return List.of(userMsg, assistantMsg);
                });

        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(memberId, sessionId, userContent);

        List<ServerSentEvent<String>> events = stream.collectList().block();

        assertThat(events).isNotNull().hasSize(4);
        assertThat(events.get(0).event()).isEqualTo("message");
        assertThat(events.get(0).data()).isEqualTo("{\"text\":\"된장\"}");
        assertThat(events.get(1).event()).isEqualTo("message");
        assertThat(events.get(1).data()).isEqualTo("{\"text\":\"찌개\"}");
        assertThat(events.get(2).event()).isEqualTo("message");
        assertThat(events.get(2).data()).isEqualTo("{\"text\":\" 레시피\"}");

        assertThat(events.get(3).event()).isEqualTo("done");
        assertThat(events.get(3).data()).contains("\"userMessageId\":100");
        assertThat(events.get(3).data()).contains("\"assistantMessageId\":101");

        // saveExchange가 boundedElastic 스레드에서 실행되었는지 검증
        assertThat(executedThreadName.get())
                .isNotNull()
                .contains("boundedElastic");

        verify(chatMessageService, times(1))
                .saveExchange(sessionId, userContent, "된장찌개 레시피");
        verify(chatDailyLimitService, times(1)).checkAndIncrement(memberId);
    }

    @Test
    @DisplayName("생성 중 BusinessException 실패: error 이벤트(비즈니스 코드) 방출 후 저장하지 않는다")
    void streamMessageBusinessErrorEmitsErrorEventAndDoesNotSave() {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "질문";

        when(chatReplyGenerator.generate(sessionId, userContent))
                .thenReturn(Flux.error(new BusinessException(ErrorCode.INVALID_CHAT_ROOM_STATUS)));

        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(memberId, sessionId, userContent);

        List<ServerSentEvent<String>> events = stream.collectList().block();

        assertThat(events).isNotNull().hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("error");
        assertThat(events.get(0).data()).contains("\"code\":\"CHAT_002\"");

        verify(chatMessageService, never()).saveExchange(any(), any(), any());
    }

    @Test
    @DisplayName("생성 중 일반 RuntimeException 실패: error 이벤트(COMMON_002) 방출 후 저장하지 않는다")
    void streamMessageGeneralErrorEmitsInternalServerErrorEventAndDoesNotSave() {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "질문";

        when(chatReplyGenerator.generate(sessionId, userContent))
                .thenReturn(Flux.error(new RuntimeException("AI API Connection Timeout")));

        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(memberId, sessionId, userContent);

        List<ServerSentEvent<String>> events = stream.collectList().block();

        assertThat(events).isNotNull().hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("error");
        assertThat(events.get(0).data()).contains("\"code\":\"COMMON_002\"");

        verify(chatMessageService, never()).saveExchange(any(), any(), any());
    }

    @Test
    @DisplayName("중간에 연결이 끊기면(cancel) saveExchange를 호출하지 않고 저장하지 않는다")
    void streamMessageCancelledDoesNotSave() {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "질문";

        when(chatReplyGenerator.generate(sessionId, userContent))
                .thenReturn(Flux.just("조각1", "조각2", "조각3"));

        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(memberId, sessionId, userContent);

        // 첫 번째 조각만 받고 즉시 cancel
        ServerSentEvent<String> firstEvent = stream.take(1).blockLast();

        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent.data()).isEqualTo("{\"text\":\"조각1\"}");

        verify(chatMessageService, never()).saveExchange(any(), any(), any());
    }

    @Test
    @DisplayName("세션 검증 실패(404, 409): 스트림 생성 전 동기 예외를 던진다")
    void streamMessageValidationFailureThrowsSynchronously() {
        Long memberId = 1L;
        Long sessionId = 999L;

        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .when(chatMessageService).validateSessionForSending(memberId, sessionId);

        assertThatThrownBy(() -> chatStreamService.streamMessage(memberId, sessionId, "안녕"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);

        verify(chatReplyGenerator, never()).generate(any(), any());
        verify(chatDailyLimitService, never()).checkAndIncrement(any());
    }

    @Test
    @DisplayName("하루 질문 한도 초과 시 스트림 생성 전 동기 429 CHAT_DAILY_LIMIT_EXCEEDED 예외를 던지고 답을 생성하지 않는다")
    void streamMessageDailyLimitExceededThrowsSynchronously() {
        Long memberId = 1L;
        Long sessionId = 10L;

        doThrow(new BusinessException(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED))
                .when(chatDailyLimitService).checkAndIncrement(memberId);

        assertThatThrownBy(() -> chatStreamService.streamMessage(memberId, sessionId, "안녕"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);

        verify(chatReplyGenerator, never()).generate(any(), any());
        verify(chatMessageService, never()).saveExchange(any(), any(), any());
    }

    @Test
    @DisplayName("공백으로 시작하는 답변 조각(\" 계란\")이 JSON 안에 그대로 유지된다")
    void streamMessagePreservesLeadingSpaceInJson() {
        Long memberId = 1L;
        Long sessionId = 10L;
        String userContent = "재료 뭐 있어?";

        when(chatReplyGenerator.generate(sessionId, userContent))
                .thenReturn(Flux.just(" 계란", " 양파 "));

        when(chatMessageService.saveExchange(eq(sessionId), eq(userContent), eq(" 계란 양파 ")))
                .thenReturn(List.of(
                        new ChatMessageResponse(100L, 1, ChatMessageRole.USER, userContent, LocalDateTime.now()),
                        new ChatMessageResponse(101L, 2, ChatMessageRole.ASSISTANT, " 계란 양파 ", LocalDateTime.now())
                ));

        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(memberId, sessionId, userContent);
        List<ServerSentEvent<String>> events = stream.collectList().block();

        assertThat(events).isNotNull().hasSize(3);
        assertThat(events.get(0).event()).isEqualTo("message");
        assertThat(events.get(0).data()).isEqualTo("{\"text\":\" 계란\"}");
        assertThat(events.get(1).event()).isEqualTo("message");
        assertThat(events.get(1).data()).isEqualTo("{\"text\":\" 양파 \"}");
    }
}

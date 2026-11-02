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
import reactor.test.StepVerifier;
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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatStreamServiceTest {

    private static final String LOCK_TOKEN = "lock-token";

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatStreamLockService chatStreamLockService;

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
        verify(chatStreamLockService, never()).acquire(any());
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

    @Test
    @DisplayName("다른 창에서 답을 받는 중이면 409 CHAT_REPLY_IN_PROGRESS를 동기로 던지고 하루 횟수를 쓰지 않는다")
    void streamMessageLockFailureThrowsWithoutUsingDailyCount() {
        Long memberId = 1L;
        Long sessionId = 10L;

        when(chatStreamLockService.acquire(sessionId))
                .thenThrow(new BusinessException(ErrorCode.CHAT_REPLY_IN_PROGRESS));

        assertThatThrownBy(() -> chatStreamService.streamMessage(memberId, sessionId, "안녕"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_REPLY_IN_PROGRESS);

        verify(chatDailyLimitService, never()).checkAndIncrement(any());
        verify(chatReplyGenerator, never()).generate(any(), any());
        verify(chatStreamLockService, never()).release(any(), any());
    }

    @Test
    @DisplayName("하루 횟수 초과로 실패하면 잡은 잠금을 풀고 그 예외를 그대로 던진다")
    void streamMessageDailyLimitFailureReleasesLock() {
        Long memberId = 1L;
        Long sessionId = 10L;

        when(chatStreamLockService.acquire(sessionId)).thenReturn(LOCK_TOKEN);
        doThrow(new BusinessException(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED))
                .when(chatDailyLimitService).checkAndIncrement(memberId);

        assertThatThrownBy(() -> chatStreamService.streamMessage(memberId, sessionId, "안녕"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);

        verify(chatStreamLockService, timeout(1_000).times(1)).release(sessionId, LOCK_TOKEN);
        verify(chatReplyGenerator, never()).generate(any(), any());
    }

    @Test
    @DisplayName("답을 끝까지 받으면 done 뒤에 잠금을 한 번 푼다")
    void streamMessageCompletionReleasesLock() {
        Long sessionId = 10L;
        when(chatStreamLockService.acquire(sessionId)).thenReturn(LOCK_TOKEN);
        when(chatReplyGenerator.generate(sessionId, "질문")).thenReturn(Flux.just("답"));
        when(chatMessageService.saveExchange(sessionId, "질문", "답")).thenReturn(List.of(
                new ChatMessageResponse(100L, 1, ChatMessageRole.USER, "질문", LocalDateTime.now()),
                new ChatMessageResponse(101L, 2, ChatMessageRole.ASSISTANT, "답", LocalDateTime.now())
        ));

        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(1L, sessionId, "질문");
        verify(chatStreamLockService, never()).release(any(), any());

        StepVerifier.create(stream)
                .expectNextMatches(event -> "message".equals(event.event()))
                .expectNextMatches(event -> "done".equals(event.event()))
                .verifyComplete();

        verify(chatStreamLockService, timeout(1_000).times(1)).release(sessionId, LOCK_TOKEN);
    }

    @Test
    @DisplayName("답 생성이 실패해 error 이벤트로 끝나도 잠금을 푼다")
    void streamMessageErrorReleasesLock() {
        Long sessionId = 10L;
        when(chatStreamLockService.acquire(sessionId)).thenReturn(LOCK_TOKEN);
        when(chatReplyGenerator.generate(sessionId, "질문"))
                .thenReturn(Flux.error(new RuntimeException("AI API Connection Timeout")));

        StepVerifier.create(chatStreamService.streamMessage(1L, sessionId, "질문"))
                .expectNextMatches(event -> "error".equals(event.event()))
                .verifyComplete();

        verify(chatStreamLockService, timeout(1_000).times(1)).release(sessionId, LOCK_TOKEN);
    }

    @Test
    @DisplayName("답을 받다가 연결이 끊겨(cancel) 끝나도 잠금을 푼다")
    void streamMessageCancelReleasesLock() {
        Long sessionId = 10L;
        when(chatStreamLockService.acquire(sessionId)).thenReturn(LOCK_TOKEN);
        when(chatReplyGenerator.generate(sessionId, "질문"))
                .thenReturn(Flux.concat(Flux.just("조각1"), Flux.never()));

        StepVerifier.create(chatStreamService.streamMessage(1L, sessionId, "질문"))
                .expectNextMatches(event -> "message".equals(event.event()))
                .thenCancel()
                .verify();

        verify(chatStreamLockService, timeout(1_000).times(1)).release(sessionId, LOCK_TOKEN);
        verify(chatMessageService, never()).saveExchange(any(), any(), any());
    }
}

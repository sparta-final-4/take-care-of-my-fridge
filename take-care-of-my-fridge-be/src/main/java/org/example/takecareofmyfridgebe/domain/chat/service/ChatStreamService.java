package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatDoneEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatErrorEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatMessageService chatMessageService;
    private final ChatStreamLockService chatStreamLockService;
    private final ChatDailyLimitService chatDailyLimitService;
    private final ChatReplyGenerator chatReplyGenerator;
    private final ChatIntentFacade chatIntentFacade;
    private final ObjectMapper objectMapper;

    /**
     * 상담 세션의 메시지 전송 및 답변 생성 과정을 SSE 스트림으로 조립한다.
     * 세션 사전 검증, 답 받기 잠금, 일일 메시지 제한 검증은 스트림 생성 전에 동기로 실행되며,
     * 의도·조건 뽑기는 답 생성 전에 boundedElastic에서 실행된다.
     * 답변 완료 후 DB 저장은 boundedElastic 스케줄러에서 비동기 I/O로 격리된다.
     * 의도 판단을 포함해 메시지 하나를 하루 1회로 센다. 답 조각을 하나도 못 받고 실패하거나 빈 답으로 끝나면 하루 횟수를 돌려준다.
     * 첫 조각 뒤에 실패하거나 사용자가 끊으면(cancel) 돌려주지 않는다.
     *
     * @param memberId  회원 ID
     * @param sessionId 세션 ID
     * @param request   사용자 질문 내용과 추천 조건(모드·선택 재료)
     * @return SSE 스트림 Flux
     */
    public Flux<ServerSentEvent<String>> streamMessage(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        String content = request.content();

        // 1. 세션 사전 검증 (스트림 시작 전에 동기 검증하여 실패 시 일반 JSON 에러로 즉시 처리)
        chatMessageService.validateSessionForSending(memberId, sessionId);

        // 2. 답 받기 잠금 (같은 상담에 이미 답을 받는 요청이 있으면 409 JSON 에러, 하루 횟수는 쓰지 않음)
        String lockToken = chatStreamLockService.acquire(sessionId);

        // 3. 일일 메시지 횟수 검증 (잠금 획득 후 카운트 증가 및 30개 초과 시 429 JSON 에러). 실패하면 잠금을 풀고 던진다
        String limitKey;
        try {
            limitKey = chatDailyLimitService.checkAndIncrement(memberId);
        } catch (RuntimeException e) {
            chatStreamLockService.release(sessionId, lockToken);
            throw e;
        }

        StringBuilder accumulated = new StringBuilder();
        AtomicBoolean firstChunkReceived = new AtomicBoolean(false);

        // 4. 의도·조건 뽑기 후 답변 스트리밍 조각 (event: message)
        // 의도 판단은 AI·DB·검색을 부르는 블로킹 작업이라 boundedElastic에서 실행한다. 실패해도 일반 답으로 넘어가며(ChatIntentFacade),
        // 하루 횟수는 위에서 센 1회에 포함된다. 지금은 판단 결과를 로그로만 남기고 답은 그대로 일반 답을 만든다
        Flux<String> replyStream = Mono.fromRunnable(() -> chatIntentFacade.resolve(memberId, sessionId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.defer(() -> chatReplyGenerator.generate(memberId, sessionId, content)));
        Flux<ServerSentEvent<String>> messageStream = replyStream
                .doOnNext(chunk -> {
                    firstChunkReceived.set(true);
                    accumulated.append(chunk);
                })
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(toJson(new ChatMessageEventResponse(chunk)))
                        .build());

        // 5. 스트림 정상 완료 시 DB 저장 후 종료 이벤트 (event: done)
        // 블로킹 I/O(DB 저장)가 Reactor 계산/이벤트루프 스레드를 점유하지 않도록 Schedulers.boundedElastic()에서 실행
        // 조각 없이 끝난 빈 답은 저장하지 않고 오류로 보낸다 (아래 6에서 하루 횟수를 돌려준다)
        Mono<ServerSentEvent<String>> doneStream = Mono.defer(() -> {
            if (!firstChunkReceived.get()) {
                log.warn("상담 답이 비어 있음 - memberId: {}, sessionId: {}", memberId, sessionId);
                return Mono.error(new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE));
            }
            return saveExchange(sessionId, content, accumulated);
        });

        // 6. 실패 시 에러 이벤트 (event: error) 및 로깅. 첫 조각 전 실패면 하루 횟수를 돌려준다
        return Flux.concat(messageStream, doneStream)
                .onErrorResume(e -> {
                    if (!firstChunkReceived.get()) {
                        chatDailyLimitService.refund(limitKey);
                    }
                    return Flux.just(errorEvent(sessionId, e));
                })
                // 7. 완료·오류·끊김(cancel) 어느 쪽으로 끝나도 잠금을 푼다
                .doFinally(signal -> chatStreamLockService.release(sessionId, lockToken));
    }

    private Mono<ServerSentEvent<String>> saveExchange(Long sessionId, String content, StringBuilder accumulated) {
        return Mono.fromCallable(() -> {
            List<ChatMessageResponse> saved = chatMessageService.saveExchange(
                    sessionId,
                    content,
                    accumulated.toString()
            );
            String doneJson = toJson(new ChatDoneEventResponse(saved.get(0).id(), saved.get(1).id()));
            return ServerSentEvent.<String>builder()
                    .event("done")
                    .data(doneJson)
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ServerSentEvent<String> errorEvent(Long sessionId, Throwable e) {
        if (e instanceof BusinessException be) {
            log.warn("상담 스트리밍 비즈니스 예외 발생 - sessionId: {}, errorCode: {}", sessionId, be.getErrorCode().getCode());
        } else {
            log.error("상담 스트리밍 예외 발생 - sessionId: {}", sessionId, e);
        }

        String errorCode = (e instanceof BusinessException be)
                ? be.getErrorCode().getCode()
                : ErrorCode.INTERNAL_SERVER_ERROR.getCode();
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(toJson(new ChatErrorEventResponse(errorCode)))
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}

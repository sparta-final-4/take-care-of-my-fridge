package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatDoneEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatErrorEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatMessageService chatMessageService;
    private final ChatDailyLimitService chatDailyLimitService;
    private final ChatReplyGenerator chatReplyGenerator;
    private final ObjectMapper objectMapper;

    /**
     * 상담 세션의 메시지 전송 및 답변 생성 과정을 SSE 스트림으로 조립한다.
     * 세션 사전 검증과 일일 메시지 제한 검증은 스트림 생성 전에 동기로 실행되며,
     * 답변 완료 후 DB 저장은 boundedElastic 스케줄러에서 비동기 I/O로 격리된다.
     *
     * @param memberId  회원 ID
     * @param sessionId 세션 ID
     * @param content   사용자 질문 내용
     * @return SSE 스트림 Flux
     */
    public Flux<ServerSentEvent<String>> streamMessage(Long memberId, Long sessionId, String content) {
        // 1. 세션 사전 검증 (스트림 시작 전에 동기 검증하여 실패 시 일반 JSON 에러로 즉시 처리)
        chatMessageService.validateSessionForSending(memberId, sessionId);

        // 2. 일일 메시지 횟수 검증 (세션 검증 통과 후 카운트 증가 및 30개 초과 시 429 JSON 에러)
        chatDailyLimitService.checkAndIncrement(memberId);

        StringBuilder accumulated = new StringBuilder();

        // 3. 답변 스트리밍 조각 (event: message)
        Flux<ServerSentEvent<String>> messageStream = chatReplyGenerator.generate(sessionId, content)
                .doOnNext(accumulated::append)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(toJson(new ChatMessageEventResponse(chunk)))
                        .build());

        // 4. 스트림 정상 완료 시 DB 저장 후 종료 이벤트 (event: done)
        // 블로킹 I/O(DB 저장)가 Reactor 계산/이벤트루프 스레드를 점유하지 않도록 Schedulers.boundedElastic()에서 실행
        Mono<ServerSentEvent<String>> doneStream = Mono.fromCallable(() -> {
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

        // 5. 실패 시 에러 이벤트 (event: error) 및 로깅
        return Flux.concat(messageStream, doneStream)
                .onErrorResume(e -> {
                    if (e instanceof BusinessException be) {
                        log.warn("상담 스트리밍 비즈니스 예외 발생 - sessionId: {}, errorCode: {}", sessionId, be.getErrorCode().getCode());
                    } else {
                        log.error("상담 스트리밍 예외 발생 - sessionId: {}", sessionId, e);
                    }

                    String errorCode = (e instanceof BusinessException be)
                            ? be.getErrorCode().getCode()
                            : ErrorCode.INTERNAL_SERVER_ERROR.getCode();
                    String errorJson = toJson(new ChatErrorEventResponse(errorCode));

                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(errorJson)
                            .build());
                });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}

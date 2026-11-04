package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatDoneEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatErrorEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationTextEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationToSave;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatRecommendationFacade;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    // 추천 후보가 하나도 없을 때 답 (AI를 부르지 않는다)
    static final String NO_RECIPE_REPLY = "조건에 맞는 레시피를 찾지 못했어요. 시간을 늘리거나, 선택 재료를 줄이거나, 다른 요리 종류로 다시 물어봐 주세요.";

    private final ChatMessageService chatMessageService;
    private final ChatStreamLockService chatStreamLockService;
    private final ChatDailyLimitService chatDailyLimitService;
    private final ChatReplyGenerator chatReplyGenerator;
    private final ChatIntentFacade chatIntentFacade;
    private final ChatRecommendationFacade chatRecommendationFacade;
    private final ObjectMapper objectMapper;

    /**
     * 상담 세션의 메시지 전송 및 답변 생성 과정을 SSE 스트림으로 조립한다.
     * 세션 사전 검증, 답 받기 잠금, 일일 메시지 제한 검증은 스트림 생성 전에 동기로 실행되며,
     * 의도·조건 뽑기와 (추천이면) 레시피 후보 검색은 답 생성 전에 boundedElastic에서 실행된다.
     * 추천이면 recommendations 이벤트(카드)를 설명 글보다 먼저 보내고, 저장하는 AI 메시지는 설명 글만이다.
     * recommendations 이벤트는 "첫 조각"으로 치지 않는다 (하루 횟수 돌려주기는 설명 글 첫 조각 기준).
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
        // 이 답에 붙은 추천 카드와 카드별 설명. 답이 끝나면 AI 메시지와 함께 저장한다
        AtomicReference<RecommendationReply> recommendationRef = new AtomicReference<>();
        List<StringBuilder> descriptions = new ArrayList<>();

        // 4. 의도·조건 뽑기 → (추천이면) 후보 검색 → 카드 (event: recommendations) → 답변 스트리밍 조각 (event: message)
        // 의도 판단·후보 검색은 AI·DB·검색을 부르는 블로킹 작업이라 boundedElastic에서 실행한다.
        // 의도 판단이 실패하면 일반 답(ChatIntentFacade), 후보 검색이 실패하면 일반 답으로 넘어간다. 하루 횟수는 위에서 센 1회에 포함된다
        Flux<ServerSentEvent<String>> messageStream = Mono.fromCallable(() -> planReply(memberId, sessionId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(plan -> {
                    recommendationRef.set(plan.recommendation());
                    Flux<String> reply = replyText(memberId, sessionId, content, plan);
                    if (plan.recommendation() == null) {
                        return reply
                                .doOnNext(chunk -> {
                                    firstChunkReceived.set(true);
                                    accumulated.append(chunk);
                                })
                                .map(this::messageEvent);
                    }
                    Flux<ServerSentEvent<String>> textEvents = plan.recommendation().event().empty()
                            ? reply.doOnNext(chunk -> {
                                firstChunkReceived.set(true);
                                accumulated.append(chunk);
                            }).map(this::messageEvent)
                            : splitRecommendationText(reply, plan.recommendation(), descriptions, accumulated,
                            firstChunkReceived);
                    ServerSentEvent<String> cards = ServerSentEvent.<String>builder()
                            .event("recommendations")
                            .data(toJson(plan.recommendation().event()))
                            .build();
                    return Flux.concat(Mono.just(cards), textEvents);
                });

        // 5. 스트림 정상 완료 시 DB 저장 후 종료 이벤트 (event: done)
        // 블로킹 I/O(DB 저장)가 Reactor 계산/이벤트루프 스레드를 점유하지 않도록 Schedulers.boundedElastic()에서 실행
        // 조각 없이 끝난 빈 답은 저장하지 않고 오류로 보낸다 (아래 6에서 하루 횟수를 돌려준다)
        Mono<ServerSentEvent<String>> doneStream = Mono.defer(() -> {
            if (!firstChunkReceived.get()) {
                log.warn("상담 답이 비어 있음 - memberId: {}, sessionId: {}", memberId, sessionId);
                return Mono.error(new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE));
            }
            return saveExchange(sessionId, content, accumulated, recommendationRef.get(), descriptions);
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

    // 답 방식 정하기: 의도가 추천이면 후보를 찾아 카드·설명 재료를 만든다. 후보 검색이 실패하면 일반 답
    private ReplyPlan planReply(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        ChatIntentResolution resolution = chatIntentFacade.resolve(memberId, sessionId, request);
        if (resolution == null || resolution.intent() != ChatIntent.RECOMMEND || resolution.recipeQuery() == null) {
            return ReplyPlan.GENERAL;
        }
        try {
            // 같은 상담에서 이미 추천한 레시피는 뒤로 보낸다
            RecipeQuery query = resolution.recipeQuery()
                    .withAlreadyRecommended(chatMessageService.findRecommendedRecipeIds(sessionId));
            RecommendationReply recommendation = chatRecommendationFacade.recommend(request.content(), query);
            // 고른 레시피·맞은 방식·카드 이유는 ChatRecommendationFacade가 "상담 추천 후보 -"로 남긴다
            return new ReplyPlan(recommendation);
        } catch (RuntimeException e) {
            log.warn("상담 추천 후보 검색 실패, 일반 답으로 - memberId: {}, sessionId: {}", memberId, sessionId, e);
            return ReplyPlan.GENERAL;
        }
    }

    private Flux<String> replyText(Long memberId, Long sessionId, String content, ReplyPlan plan) {
        if (plan.recommendation() == null) {
            return chatReplyGenerator.generate(memberId, sessionId, content);
        }
        // 카드가 0개면 AI를 부르지 않고 정해진 답을 보낸다. "찾지 못했어요"는 카드가 없을 때만 나간다
        if (plan.recommendation().event().empty()) {
            return Flux.just(NO_RECIPE_REPLY);
        }
        return chatReplyGenerator.generateRecommendation(memberId, sessionId, content, plan.recommendation().candidateInfo());
    }

    // recommendation이 null이면 일반 답
    private record ReplyPlan(RecommendationReply recommendation) {
        private static final ReplyPlan GENERAL = new ReplyPlan(null);
    }

    // 추천 답을 표시로 나눠 [소개]는 message, [n]은 recommendation_text 이벤트로 보낸다.
    // 표시를 빼먹은 답은 전체를 소개로 보낸다. "첫 조각"은 처음 내보낸 글(소개든 카드 설명이든)이다
    private Flux<ServerSentEvent<String>> splitRecommendationText(
            Flux<String> reply,
            RecommendationReply recommendation,
            List<StringBuilder> descriptions,
            StringBuilder intro,
            AtomicBoolean firstChunkReceived
    ) {
        int cardCount = recommendation.event().recipes().size();
        for (int i = 0; i < cardCount; i++) {
            descriptions.add(new StringBuilder());
        }
        RecommendationTextSplitter splitter = new RecommendationTextSplitter(cardCount);
        return reply.concatMapIterable(splitter::feed)
                .concatWith(Flux.defer(() -> Flux.fromIterable(splitter.finish())))
                .doOnNext(piece -> {
                    firstChunkReceived.set(true);
                    (piece.intro() ? intro : descriptions.get(piece.index())).append(piece.text());
                })
                .map(piece -> piece.intro()
                        ? messageEvent(piece.text())
                        : ServerSentEvent.<String>builder()
                        .event("recommendation_text")
                        .data(toJson(new ChatRecommendationTextEventResponse(piece.index(), piece.text())))
                        .build());
    }

    private ServerSentEvent<String> messageEvent(String text) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(toJson(new ChatMessageEventResponse(text)))
                .build();
    }

    private Mono<ServerSentEvent<String>> saveExchange(Long sessionId, String content, StringBuilder accumulated,
                                                       RecommendationReply recommendation, List<StringBuilder> descriptions) {
        return Mono.fromCallable(() -> {
            List<RecommendedRecipe> recipes = recommendation == null ? List.of() : recommendation.event().recipes();
            List<RecommendationToSave> cards = new ArrayList<>();
            List<Integer> receivedNumbers = new ArrayList<>();
            for (int i = 0; i < recipes.size(); i++) {
                String description = i < descriptions.size() ? descriptions.get(i).toString().strip() : "";
                cards.add(new RecommendationToSave(recipes.get(i).recipeId(), description.isEmpty() ? null : description));
                if (!description.isEmpty()) {
                    receivedNumbers.add(i + 1);
                }
            }
            // 설명이 빠진 카드는 화면에서 카드 이유 한 줄로 대신한다. 빠진 걸 알 수 있게 남긴다
            if (!cards.isEmpty() && receivedNumbers.size() < cards.size()) {
                log.warn("카드 설명 누락 - sessionId: {}, 카드 수: {}, 받은 번호: {}", sessionId, cards.size(), receivedNumbers);
            }
            // 카드가 없으면 기존처럼 메시지만 저장한다
            List<ChatMessageResponse> saved = cards.isEmpty()
                    ? chatMessageService.saveExchange(sessionId, content, accumulated.toString())
                    : chatMessageService.saveExchange(sessionId, content, accumulated.toString(), cards);
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

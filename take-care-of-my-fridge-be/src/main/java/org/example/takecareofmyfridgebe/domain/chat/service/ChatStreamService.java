package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatHealthWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatDoneEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatErrorEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.DetailQuestion;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationTextEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ProductCard;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeDetailReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationToSave;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSafetyBlock;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatContextFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatFridgeShopFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatRecommendationFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.HealthWordChecker;
import org.example.takecareofmyfridgebe.domain.chat.facade.SafetyWordChecker;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    // 추천 후보가 하나도 없을 때 답 (AI를 부르지 않는다)
    static final String NO_RECIPE_REPLY = "조건에 맞는 레시피를 찾지 못했어요. 시간을 늘리거나, 선택 재료를 줄이거나, 다른 요리 종류로 다시 물어봐 주세요.";
    // 물어본 레시피가 지워졌을 때 답 (AI를 부르지 않는다)
    static final String RECIPE_GONE_REPLY = "물어보신 레시피는 지금 볼 수 없어요. 다른 레시피를 물어봐 주세요.";
    // 요리와 상관없는 질문(의도 OFF_TOPIC)의 답 (답 생성 AI를 부르지 않는다. 의도 판단 AI를 썼으므로 하루 횟수는 센다)
    static final String OFF_TOPIC_REPLY = "저는 요리, 냉장고 재료, 장보기 상담만 도와드릴 수 있어요. 냉장고 재료로 만들 요리를 추천해 드릴까요?";
    // 사용자가 보낸 말에 건강 관련 말이 있으면 답 끝에 빈 줄 하나를 두고 붙이는 안내 (추천 답은 소개 글 끝, 곧 카드 바로 위)
    static final String HEALTH_NOTICE = "일반적인 정보예요. 질환·알레르기·임신·복용 중인 약과 관련된 판단은 의사나 약사와 상담해 주세요.";
    static final String HEALTH_NOTICE_SEPARATOR = "\n\n";
    // 추천에서 알레르기·못 먹는 재료를 뺐을 때 답 끝(카드 바로 위)에 붙이는 안내. 앞에 "땅콩이"·"땅콩·새우가"를 붙인다
    static final String ALLERGY_NOTICE_TAIL = " 들어간 레시피는 뺐어요. 양념이나 가공품에 성분이 들어 있을 수 있으니 재료를 꼭 확인해 주세요.";

    private final ChatMessageService chatMessageService;
    private final ChatStreamLockService chatStreamLockService;
    private final ChatDailyLimitService chatDailyLimitService;
    private final ChatReplyGenerator chatReplyGenerator;
    private final ChatIntentFacade chatIntentFacade;
    private final ChatRecommendationFacade chatRecommendationFacade;
    private final ChatFridgeShopFacade chatFridgeShopFacade;
    private final ChatContextFacade chatContextFacade;
    private final ChatContextStore chatContextStore;
    private final ChatSafetyWordProperties chatSafetyWordProperties;
    private final ChatHealthWordProperties chatHealthWordProperties;
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
     * 금지어·자해·탈취 단어에 걸린 말은 하루 횟수를 늘리기 전에 걸러, AI 없이 안내 답을 보내고 횟수를 세지 않는다.
     * 의도 판단이 상관없는 질문(OFF_TOPIC)·목록 밖 욕설·탈취(BLOCKED)·자해(SELF_HARM)면 답 생성 AI 없이 안내 답을 보내고,
     * 의도 판단 AI를 썼으므로 하루 횟수는 센다 (AI를 썼으면 센다). BLOCKED·SELF_HARM은 걸러진 메시지(filtered)로 저장한다.
     * 보낸 말에 건강 관련 말(질환·알레르기·임신·약)이 있으면 답 끝에 상담 안내를 message로 하나 더 보내고 같이 저장한다 (하루 횟수는 센다).
     * 안전 장치 안내에는 붙이지 않고, 답 조각이 하나도 없으면 붙이지 않는다 (안내는 첫 조각으로 치지 않는다).
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

        // 3. AI를 부르기 전에 단어로 거른다 (잠금 뒤, 하루 횟수 전). 걸리면 하루 횟수를 세지 않고 안내 답을 보낸다
        Optional<ChatSafetyBlock> block = SafetyWordChecker.check(content, chatSafetyWordProperties);
        if (block.isPresent()) {
            return blockedReply(memberId, sessionId, content, block.get(), lockToken);
        }

        // 4. 일일 메시지 횟수 검증 (잠금 획득 후 카운트 증가 및 30개 초과 시 429 JSON 에러). 실패하면 잠금을 풀고 던진다
        String limitKey;
        try {
            limitKey = chatDailyLimitService.checkAndIncrement(memberId);
        } catch (RuntimeException e) {
            chatStreamLockService.release(sessionId, lockToken);
            throw e;
        }

        StringBuilder accumulated = new StringBuilder();
        AtomicBoolean firstChunkReceived = new AtomicBoolean(false);
        // 이 답의 계획(추천이면 조건·카드)과 카드별 설명. 답이 끝나면 AI 메시지와 함께 저장한다
        AtomicReference<ReplyPlan> planRef = new AtomicReference<>();
        List<StringBuilder> descriptions = new ArrayList<>();

        // 5. 의도·조건 뽑기 → (추천이면) 후보 검색 → 카드 (event: recommendations) → 답변 스트리밍 조각 (event: message)
        // 의도 판단·후보 검색은 AI·DB·검색을 부르는 블로킹 작업이라 boundedElastic에서 실행한다.
        // 의도 판단이 실패하면 일반 답(ChatIntentFacade), 후보 검색이 실패하면 일반 답으로 넘어간다. 하루 횟수는 위에서 센 1회에 포함된다
        Flux<ServerSentEvent<String>> messageStream = Mono.fromCallable(() -> planReply(memberId, sessionId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(plan -> {
                    planRef.set(plan);
                    Flux<String> reply = replyText(memberId, sessionId, content, plan);
                    // 레시피 자세히: 그 레시피 카드 하나를 먼저 보내고 글은 표시 없이 그대로 message로 보낸다
                    if (plan.detail() != null) {
                        ServerSentEvent<String> card = ServerSentEvent.<String>builder()
                                .event("recommendations")
                                .data(toJson(plan.detail().event()))
                                .build();
                        // 생략 괄호 글("(뒤 단계 생략)")은 화면·저장 모두에서 지운다
                        OmissionNoteFilter omissionFilter = new OmissionNoteFilter();
                        Flux<String> text = reply
                                .doOnNext(chunk -> firstChunkReceived.set(true))
                                .map(omissionFilter::feed)
                                .concatWith(Flux.defer(() -> Flux.just(omissionFilter.finish())))
                                .filter(piece -> !piece.isEmpty())
                                .doOnNext(accumulated::append);
                        return Flux.concat(Mono.just(card), text.map(this::messageEvent));
                    }
                    if (plan.recommendation() == null) {
                        return reply
                                .doOnNext(chunk -> {
                                    firstChunkReceived.set(true);
                                    accumulated.append(chunk);
                                })
                                .map(this::messageEvent);
                    }
                    // 카드가 없거나(정해진 답) 밀키트만 보여 주는 답(서버 글)은 표시 없이 그대로 message로 보낸다
                    Flux<ServerSentEvent<String>> textEvents = plan.recommendation().event().empty()
                            || plan.recommendation().fixedReply() != null
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

        // 건강 안내: 보낸 말만 본다 (AI 답은 보지 않는다). 답이 끝난 뒤 빈 줄 하나를 두고 붙여 화면·저장 모두 답 끝에 둔다
        boolean healthMentioned = HealthWordChecker.mentions(content, chatHealthWordProperties);
        // 알레르기 안내(추천 답에서 재료를 뺐을 때)가 있으면 건강 안내 앞에 한 메시지로 붙인다
        Flux<ServerSentEvent<String>> healthNotice = Flux.defer(() -> {
            ReplyPlan plan = planRef.get();
            if (plan == null || plan.serverNotice() || !firstChunkReceived.get()) {
                return Flux.empty();
            }
            List<String> lines = new ArrayList<>();
            if (plan.recommendation() != null && !plan.allergens().isEmpty()) {
                lines.add(allergyNotice(plan.allergens()));
            }
            if (healthMentioned) {
                lines.add(HEALTH_NOTICE);
                log.info("상담 건강 관련 말이라 답 끝에 안내를 붙임 - memberId: {}, sessionId: {}", memberId, sessionId);
            }
            if (lines.isEmpty()) {
                return Flux.empty();
            }
            String text = String.join("\n", lines);
            String notice = accumulated.isEmpty() ? text : HEALTH_NOTICE_SEPARATOR + text;
            accumulated.append(notice);
            return Flux.just(messageEvent(notice));
        });

        // 6. 스트림 정상 완료 시 DB 저장 후 종료 이벤트 (event: done)
        // 블로킹 I/O(DB 저장)가 Reactor 계산/이벤트루프 스레드를 점유하지 않도록 Schedulers.boundedElastic()에서 실행
        // 조각 없이 끝난 빈 답은 저장하지 않고 오류로 보낸다 (아래 7에서 하루 횟수를 돌려준다)
        Mono<ServerSentEvent<String>> doneStream = Mono.defer(() -> {
            if (!firstChunkReceived.get()) {
                log.warn("상담 답이 비어 있음 - memberId: {}, sessionId: {}", memberId, sessionId);
                return Mono.error(new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE));
            }
            return saveExchange(sessionId, content, accumulated, planRef.get(), descriptions);
        });

        // 7. 실패 시 에러 이벤트 (event: error) 및 로깅. 첫 조각 전 실패면 하루 횟수를 돌려준다
        // 의도 판단으로 서버 안내를 보낸 답(OFF_TOPIC·BLOCKED·SELF_HARM)은 의도 판단 AI를 썼으므로 센다 (안내 글이 첫 조각이라 돌려주지 않는다)
        return Flux.concat(messageStream, healthNotice, doneStream)
                .onErrorResume(e -> {
                    if (!firstChunkReceived.get()) {
                        chatDailyLimitService.refund(limitKey);
                    }
                    return Flux.just(errorEvent(sessionId, e));
                })
                // 8. 완료·오류·끊김(cancel) 어느 쪽으로 끝나도 잠금을 푼다
                .doFinally(signal -> chatStreamLockService.release(sessionId, lockToken));
    }

    // 답 방식 정하기: 상담 문맥(최근 대화)을 읽고, 의도가 추천이면 후보를 찾아 카드·설명 재료를 만든다. 후보 검색이 실패하면 일반 답
    private ReplyPlan planReply(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        ChatContext context = chatContextFacade.load(sessionId);
        ChatIntentResolution resolution = chatIntentFacade.resolve(memberId, sessionId, request, context);
        if (resolution != null && resolution.declines()) {
            Declined declined = Declined.of(resolution.intent());
            log.info("상담 의도 판단으로 AI 답 없이 안내 - memberId: {}, sessionId: {}, 의도: {}", memberId, sessionId, resolution.intent());
            return ReplyPlan.declined(context, declined);
        }
        if (resolution != null && resolution.intent() == ChatIntent.RECIPE_DETAIL) {
            // 사용자 말 대신 질문 종류로 만든 질문을 넘긴다 ("두 번째 거"를 단계 번호로 알아듣지 않게). 저장하는 사용자 메시지는 원래 말
            String question = DetailQuestion.questionFor(resolution.detailQuestion(), request.content());
            return planRecipeDetail(memberId, sessionId, resolution.targetRecipeId(), question, context);
        }
        if (resolution != null && (resolution.intent() == ChatIntent.FRIDGE || resolution.intent() == ChatIntent.SHOP)) {
            return planFridgeOrShop(memberId, sessionId, resolution, context);
        }
        if (resolution == null || !resolution.recommends()) {
            return ReplyPlan.general(context);
        }
        try {
            // 같은 상담에서 이미 추천한 레시피는 뒤로 보낸다
            RecipeQuery query = resolution.recipeQuery()
                    .withAlreadyRecommended(chatMessageService.findRecommendedRecipeIds(sessionId));
            // 다시 추천이면 직전 카드는 후보에서 뺀다 (모자라면 뒤에 다시 넣는다)
            RecommendationReply recommendation = resolution.intent() == ChatIntent.REFINE && context.lastRecommendation() != null
                    ? chatRecommendationFacade.recommend(request.content(), query, context.lastRecommendation().recipeIds(), sessionId)
                    : chatRecommendationFacade.recommend(request.content(), query, List.of(), sessionId);
            // 고른 레시피·맞은 방식·카드 이유는 ChatRecommendationFacade가 "상담 추천 후보 -"로 남긴다
            return new ReplyPlan(context, recommendation, resolution.recipeQuery(), null, false, null, resolution.allergens());
        } catch (RuntimeException e) {
            log.warn("상담 추천 후보 검색 실패, 일반 답으로 - memberId: {}, sessionId: {}", memberId, sessionId, e);
            return ReplyPlan.general(context);
        }
    }

    // 냉장고 보기·사기: 냉장고·상품 카드와 서버 문장 (답 생성 AI 없음, 냉장고 카드는 저장하지 않는다). 읽다 실패하면 일반 답
    private ReplyPlan planFridgeOrShop(Long memberId, Long sessionId, ChatIntentResolution resolution, ChatContext context) {
        try {
            RecommendationReply reply = resolution.intent() == ChatIntent.FRIDGE
                    ? chatFridgeShopFacade.fridge(memberId, resolution.lookup())
                    : chatFridgeShopFacade.shop(memberId, resolution.lookup());
            return new ReplyPlan(context, reply, null);
        } catch (RuntimeException e) {
            log.warn("상담 {} 답 실패, 일반 답으로 - memberId: {}, sessionId: {}", resolution.intent(), memberId, sessionId, e);
            return ReplyPlan.general(context);
        }
    }

    // 레시피 자세히: 레시피 정보와 카드 하나를 만든다. 레시피가 지워졌으면 정해진 답, 읽다 실패하면 일반 답
    private ReplyPlan planRecipeDetail(Long memberId, Long sessionId, Long recipeId, String question, ChatContext context) {
        try {
            return chatRecommendationFacade.describe(memberId, recipeId)
                    .map(detail -> new ReplyPlan(context, null, null, detail.withQuestion(question), false, null, List.of()))
                    .orElseGet(() -> {
                        log.info("상담 레시피 자세히 대상이 지워짐 - memberId: {}, sessionId: {}, recipeId: {}", memberId, sessionId, recipeId);
                        return new ReplyPlan(context, null, null, null, true, null, List.of());
                    });
        } catch (RuntimeException e) {
            log.warn("상담 레시피 자세히 정보 읽기 실패, 일반 답으로 - memberId: {}, sessionId: {}, recipeId: {}",
                    memberId, sessionId, recipeId, e);
            return ReplyPlan.general(context);
        }
    }

    // "땅콩이 들어간 레시피는 뺐어요. …" / "땅콩·새우가 …". 이·가는 마지막 이름의 받침으로 고른다
    static String allergyNotice(List<String> names) {
        String joined = String.join("·", names);
        char last = joined.charAt(joined.length() - 1);
        boolean hasFinalConsonant = last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
        return joined + (hasFinalConsonant ? "이" : "가") + ALLERGY_NOTICE_TAIL;
    }

    // AI 답에 섞인 링크를 지운다 (일반·추천·레시피 자세히 모두, 화면과 저장 모두 지운 글. 추천 답은 [소개]·[n]으로 나누기 전).
    // 서버가 정한 글에는 링크가 없어 거치지 않는다. 답 하나마다 필터를 새로 만들고, 지우고 남은 빈 글은 보내지 않는다 (링크만 있던 답은 빈 답이라 CHAT_010)
    private static Flux<String> withoutLinks(Flux<String> reply) {
        return Flux.defer(() -> {
            LinkFilter filter = new LinkFilter();
            return reply.map(filter::feed)
                    .concatWith(Flux.defer(() -> Flux.just(filter.finish())))
                    .filter(piece -> !piece.isEmpty());
        });
    }

    private Flux<String> replyText(Long memberId, Long sessionId, String content, ReplyPlan plan) {
        if (plan.declined() != null) {
            return Flux.just(plan.declined().reply());
        }
        if (plan.recipeGone()) {
            return Flux.just(RECIPE_GONE_REPLY);
        }
        if (plan.detail() != null) {
            String question = plan.detail().question() == null ? content : plan.detail().question();
            return withoutLinks(chatReplyGenerator.generateRecipeDetail(memberId, sessionId, question, plan.detail().recipeInfo()));
        }
        if (plan.recommendation() == null) {
            // 일반 답에만 이전 대화(최근 3턴)를 넣는다. 추천 설명은 후보 정보로 충분하다
            return withoutLinks(chatReplyGenerator.generate(memberId, sessionId, content, plan.context().recentMessages()));
        }
        // 밀키트만 보여 주는 답은 AI를 부르지 않고 서버 글을 보낸다 (레시피 카드가 없어 AI가 레시피를 소개하지 않게)
        if (plan.recommendation().fixedReply() != null) {
            return Flux.just(plan.recommendation().fixedReply());
        }
        // 카드가 0개면 AI를 부르지 않고 정해진 답을 보낸다. "찾지 못했어요"는 카드가 없을 때만 나간다
        if (plan.recommendation().event().empty()) {
            return Flux.just(NO_RECIPE_REPLY);
        }
        return withoutLinks(chatReplyGenerator.generateRecommendation(memberId, sessionId, content, plan.recommendation().candidateInfo()));
    }

    /**
     * 답 방식. recommendation이 있으면 추천(query는 추천에 쓴 조건, 직전 추천으로 Redis에 둔다),
     * detail이 있으면 레시피 자세히, recipeGone이면 물어본 레시피가 지워져 정해진 답,
     * declined가 있으면 답 생성 AI 없이 서버 안내(의도 판단 AI를 썼으므로 하루 횟수는 셈), 모두 없으면 일반 답.
     */
    private record ReplyPlan(ChatContext context, RecommendationReply recommendation, RecipeQuery query,
                             RecipeDetailReply detail, boolean recipeGone, Declined declined, List<String> allergens) {

        private ReplyPlan(ChatContext context, RecommendationReply recommendation, RecipeQuery query) {
            this(context, recommendation, query, null, false, null, List.of());
        }

        private static ReplyPlan general(ChatContext context) {
            return new ReplyPlan(context, null, null);
        }

        private static ReplyPlan declined(ChatContext context, Declined declined) {
            return new ReplyPlan(context, null, null, null, false, declined, List.of());
        }

        // 의도 판단으로 답 생성 AI 없이 서버 안내를 보내는 답인지 (건강·알레르기 안내를 붙이지 않는다)
        private boolean serverNotice() {
            return declined != null;
        }

        // 보낸 말과 답을 안전 장치로 걸러진 메시지(filtered)로 저장하는지
        private boolean filtered() {
            return declined != null && declined.filtered();
        }

        // 저장할 카드 (추천 카드 또는 레시피 자세히 카드 하나)
        private List<RecommendedRecipe> cards() {
            if (recommendation != null) {
                return recommendation.event().recipes();
            }
            return detail != null ? detail.event().recipes() : List.of();
        }

        // 저장할 밀키트 카드 (추천 답에만 있다)
        private List<RecommendedMealkit> mealkits() {
            return recommendation != null ? recommendation.event().mealkits() : List.of();
        }

        private List<ProductCard> products() {
            return recommendation != null ? recommendation.event().products() : List.of();
        }
    }

    /**
     * 답 생성 AI 없이 보내는 서버 안내 답. filtered면 보낸 말과 안내 답을 안전 장치로 걸러진 메시지로 저장한다.
     * 상관없는 질문은 다른 주제일 뿐이라 표시하지 않고(문맥에 남아도 된다), 목록 밖 욕설·탈취(BLOCKED)와 자해(SELF_HARM)는 표시한다.
     */
    private record Declined(String reply, boolean filtered) {

        private static Declined of(ChatIntent intent) {
            return switch (intent) {
                case OFF_TOPIC -> new Declined(OFF_TOPIC_REPLY, false);
                // 서버 단어 목록 밖의 욕설·탈취는 금지어 안내 하나로, 자해는 상담 전화 안내로 답한다 (단어 차단과 같은 문장)
                case BLOCKED -> new Declined(ChatSafetyBlock.BANNED.reply(), true);
                case SELF_HARM -> new Declined(ChatSafetyBlock.SELF_HARM.reply(), true);
                default -> throw new IllegalArgumentException("서버 안내로 답하지 않는 의도: " + intent);
            };
        }
    }

    // 추천 답을 표시로 나눠 [소개]는 message, [n]은 recommendation_text 이벤트로 보낸다.
    // 안내 문장이 있으면 그 안내가 소개다: 서버가 먼저 보내고(message) 저장할 소개도 안내뿐이며, AI가 쓴 소개 글은 버린다.
    // 표시를 빼먹은 답은 전체를 소개로 보낸다. "첫 조각"은 AI에게서 처음 내보낸 글(소개든 카드 설명이든)이다 (안내 문장은 세지 않음)
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
        RecommendationTextSplitter splitter = new RecommendationTextSplitter(cardCount, recommendation.notices(),
                recommendation.event().recipes().stream().map(RecommendedRecipe::name).toList());
        String noticeText = recommendation.noticeText();
        boolean dropIntro = !noticeText.isEmpty();
        Flux<ServerSentEvent<String>> notice = Flux.defer(() -> {
            if (noticeText.isEmpty()) {
                return Flux.empty();
            }
            intro.append(noticeText);
            return Flux.just(messageEvent(noticeText));
        });
        Flux<ServerSentEvent<String>> text = reply.concatMapIterable(splitter::feed)
                .concatWith(Flux.defer(() -> Flux.fromIterable(splitter.finish())))
                // 버리는 소개 글도 AI 답을 받은 것이므로 첫 조각으로 센다 (하루 횟수 돌려주기 기준)
                .doOnNext(piece -> firstChunkReceived.set(true))
                .filter(piece -> !(dropIntro && piece.intro()))
                .doOnNext(piece -> (piece.intro() ? intro : descriptions.get(piece.index())).append(piece.text()))
                .map(piece -> piece.intro()
                        ? messageEvent(piece.text())
                        : ServerSentEvent.<String>builder()
                        .event("recommendation_text")
                        .data(toJson(new ChatRecommendationTextEventResponse(piece.index(), piece.text())))
                        .build());
        return Flux.concat(notice, text);
    }

    private ServerSentEvent<String> messageEvent(String text) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(toJson(new ChatMessageEventResponse(text)))
                .build();
    }

    /**
     * 단어에 걸린 말의 답: AI를 부르지 않고 안내 답 하나를 message로 보낸 뒤 저장하고 done을 보낸다.
     * 하루 횟수는 늘리지 않았으므로 돌려줄 것도 없다. 로그에는 종류만 남기고 보낸 말은 남기지 않는다.
     */
    private Flux<ServerSentEvent<String>> blockedReply(Long memberId, Long sessionId, String content, ChatSafetyBlock block,
                                                       String lockToken) {
        log.info("상담 메시지 단어 차단, AI 없이 안내 - memberId: {}, sessionId: {}, 종류: {}", memberId, sessionId, block);
        String reply = block.reply();
        Mono<ServerSentEvent<String>> done = Mono.fromCallable(() -> {
            List<ChatMessageResponse> saved = chatMessageService.saveFilteredExchange(sessionId, content, reply);
            chatContextStore.touch(sessionId);
            return doneEvent(saved);
        }).subscribeOn(Schedulers.boundedElastic());
        return Flux.concat(Mono.just(messageEvent(reply)), done)
                .onErrorResume(e -> Flux.just(errorEvent(sessionId, e)))
                .doFinally(signal -> chatStreamLockService.release(sessionId, lockToken));
    }

    private ServerSentEvent<String> doneEvent(List<ChatMessageResponse> saved) {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(toJson(new ChatDoneEventResponse(saved.get(0).id(), saved.get(1).id())))
                .build();
    }

    private Mono<ServerSentEvent<String>> saveExchange(Long sessionId, String content, StringBuilder accumulated,
                                                       ReplyPlan plan, List<StringBuilder> descriptions) {
        return Mono.fromCallable(() -> {
            List<RecommendedRecipe> recipes = plan == null ? List.of() : plan.cards();
            List<RecommendationToSave> cards = new ArrayList<>();
            List<Integer> receivedNumbers = new ArrayList<>();
            for (int i = 0; i < recipes.size(); i++) {
                String description = i < descriptions.size() ? descriptions.get(i).toString().strip() : "";
                // AI 설명이 없으면 카드에 미리 정한 설명(레시피 자세히 카드의 "물어보신 레시피예요")을 저장한다
                String saved = description.isEmpty() ? recipes.get(i).description() : description;
                cards.add(new RecommendationToSave(recipes.get(i).recipeId(), saved));
                if (!description.isEmpty()) {
                    receivedNumbers.add(i + 1);
                }
            }
            // 설명이 빠진 카드는 화면에서 카드 이유 한 줄로 대신한다. 빠진 걸 알 수 있게 남긴다 (레시피 자세히 카드는 설명이 없다)
            if (plan.recommendation() != null && !recipes.isEmpty() && receivedNumbers.size() < recipes.size()) {
                log.warn("카드 설명 누락 - sessionId: {}, 카드 수: {}, 받은 번호: {}", sessionId, recipes.size(), receivedNumbers);
            }
            // 밀키트 카드는 레시피 카드 다음 순서로, 서버가 쓴 설명과 함께 저장한다
            for (RecommendedMealkit mealkit : plan == null ? List.<RecommendedMealkit>of() : plan.mealkits()) {
                cards.add(RecommendationToSave.mealkit(mealkit.product().productId(), mealkit.description()));
            }
            // 재료 상품 카드(사기 답·냉장고 보기의 없는 재료)는 말한 이름과 함께 저장한다. 냉장고 카드는 저장하지 않는다
            for (ProductCard product : plan == null ? List.<ProductCard>of() : plan.products()) {
                cards.add(RecommendationToSave.product(product.product().productId(), product.name()));
            }
            // 카드가 없으면 기존처럼 메시지만 저장한다. 안전 장치로 걸러진 답(서버 안내)은 표시해 저장한다
            List<ChatMessageResponse> saved = plan != null && plan.filtered()
                    ? chatMessageService.saveFilteredExchange(sessionId, content, accumulated.toString())
                    : cards.isEmpty()
                    ? chatMessageService.saveExchange(sessionId, content, accumulated.toString())
                    : chatMessageService.saveExchange(sessionId, content, accumulated.toString(), cards);
            rememberContext(sessionId, plan, recipes);
            return doneEvent(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // 카드가 있는 추천 답이면 그 조건·카드를 직전 추천으로 두고, 그 밖의 답은 직전 추천을 그대로 두고 유지 시간만 늘린다
    private void rememberContext(Long sessionId, ReplyPlan plan, List<RecommendedRecipe> recipes) {
        if (plan != null && plan.query() != null && !recipes.isEmpty()) {
            chatContextStore.saveRecommendation(sessionId, plan.query(),
                    recipes.stream().map(RecommendedRecipe::recipeId).toList());
        } else {
            chatContextStore.touch(sessionId);
        }
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

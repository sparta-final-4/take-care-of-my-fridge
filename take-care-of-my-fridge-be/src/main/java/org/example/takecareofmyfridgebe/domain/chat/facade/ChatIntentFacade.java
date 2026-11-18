package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.ItemLookup;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation.QuerySnapshot;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RefineCondition;
import org.example.takecareofmyfridgebe.domain.chat.intent.FridgeShopWordChecker;
import org.example.takecareofmyfridgebe.domain.chat.intent.CorrectionInput;
import org.example.takecareofmyfridgebe.domain.chat.intent.IntentCorrector;
import org.example.takecareofmyfridgebe.domain.chat.intent.JevIntentResolver;
import org.example.takecareofmyfridgebe.domain.chat.intent.MealkitWordChecker;
import org.example.takecareofmyfridgebe.domain.chat.intent.TasteWordChecker;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyExclusionFinder;
import org.example.takecareofmyfridgebe.domain.chat.ai.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentClassifier;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담 메시지를 추천으로 처리할지 일반 답으로 처리할지 정하고, 추천이면 레시피 후보 검색 조건(RecipeQuery)을 만든다.
 * <ul>
 *   <li>mode=RECOMMEND(칩): AI를 부르지 않고 추천. 선택 재료만 쓰기는 요청값 그대로</li>
 *   <li>mode=AUTO: 의도는 Jev, 조건 값은 OpenAI 꺼내기로 정한다. 선택 재료만 쓰기는 요청값 OR AI 결과</li>
 *   <li>AI 실패: Jev 실패(키 없음·시간 초과·오류)나, 꺼낸 값이 필요한 의도(추천·레시피 자세히)인데 OpenAI 꺼내기 실패(오류·시간 초과)면
 *       질문에 답하지 않고 서버 답(선택 재료 → 냉장고 재료 → 인기 레시피 카드, ChatIntentResolution.serverFallback)으로 정한다.
 *       OpenAI를 다시 부르지 않고 에러로 끝내지 않는다. 꺼낸 값이 필요 없는 의도(일반·냉장고·사기·상관없음·거절)면 OpenAI 꺼내기 실패는
 *       무시하고 그 의도대로 답한다 (냉장고·사기 이름·범위는 서버가 말에서 찾는다)</li>
 * </ul>
 * 말한 재료 이름은 IngredientMatcher로 재료 ID로 바꾸고, 못 찾은 이름(ING_CUSTOM)은 버린다.
 * 말한 재료(includeIngredients)는 고른 재료와 합쳐 선택 재료로 쓴다 (중복 제거, 빼달라고 한 재료는 뺀다).
 * 그래서 "버섯 두부로 할 수 있는"처럼 말로만 재료를 정해도 칩으로 고른 것과 같은 규칙(선택 재료만 쓰기, 2개 이상 다 쓰면 보유 비율 예외)이 적용된다.
 * <p>
 * 직전 추천 카드가 있으면 Jev와 OpenAI 꺼내기에 [직전 추천]·[이전 추천]·[직전 조건]을 넘겨 이어지는 말을 알아듣는다.
 * <ul>
 *   <li>추천 + 이어짐(직전 조건을 이어받아 일부만 바꾸는 추천): 직전 조건과 이번 말을 합친다.
 *       직전 조건이 없으면(Redis 만료·장애) 이어짐은 아니오이고 이번 말만으로 새 추천이다 (서비스 규칙, IntentCorrector)</li>
 *   <li>RECIPE_DETAIL(레시피 자세히): 이름이 직전 카드에 있으면 그 카드, 다음은 직전 카드 번호, 다음은 상담 전체 추천에서 이름으로 찾는다.
 *       못 찾으면 일반 답(이전 대화를 보고 AI가 되묻는다)</li>
 * </ul>
 * <p>
 * 의도는 Jev 하나가 정한다: OpenAI 꺼내기와 함께 Jev를 다른 스레드에서 시작하고(JevIntentRunner), 꺼내기가 끝난 뒤 Jev 결과를 기다려
 * (제한 시간 안에서) Jev 1등 의도(confidence와 상관없이)와 OpenAI가 꺼낸 값으로 의도 결과를 만든다 (JevIntentResolver).
 * 그 뒤 서버 서비스 규칙(IntentCorrector)을 거친다.
 * Jev 1차 1등 의도가 자해(SELF_HARM)이거나 자해 확률이 {@value #SELF_HARM_MIN_PROBABILITY} 이상이면 자해 안내를 보낸다
 * (OpenAI 꺼내기나 Jev 2차가 실패했어도). Jev 1차가 실패하면 OpenAI 꺼내기의 자해 뜻(selfHarmRisk)이 true일 때 자해 안내를 보내고,
 * 아니면 서버 답이다. Jev 1차가 성공하면 selfHarmRisk는 쓰지 않는다.
 * 단어 차단(SafetyWordChecker)은 이보다 먼저 ChatStreamFacade가 본다.
 * 무엇으로 정했는지는 "상담 의도 Jev 결정" 로그로 남긴다. 설계: docs/testing/jev-on-design.md
 */
@Slf4j
@Component
public class ChatIntentFacade {

    // Jev가 고를 분류 목록에서만 뺄 분류. 뜻이 없어 조건으로 쓸 수 없다 (레시피 후보에서는 빼지 않는다)
    private static final Set<String> PROMPT_EXCLUDED_CATEGORIES = Set.of("기타");
    // OpenAI가 꺼낸 값(요리 이름·키워드·재료·레시피 자세히 대상)이 있어야 답할 수 있는 의도. 꺼내기가 실패하면 서버 답으로 간다
    private static final Set<ChatIntent> NEEDS_EXTRACTION = Set.of(ChatIntent.RECOMMEND, ChatIntent.RECIPE_DETAIL);
    // Jev 자해(SELF_HARM) 확률이 이 값 이상이면 1등 의도가 아니어도 자해 안내를 보낸다. 평가로 정함
    // (처음 값 0.3. 자해 기준 세트 chat-intent-safety.yml로 자해 문장을 모두 잡고 아닌 문장은 잡지 않는 범위를 본다)
    static final double SELF_HARM_MIN_PROBABILITY = 0.3;
    // 의도 판단에 넘길 [이전 추천] 이름 수
    private static final int EARLIER_CARDS_SHOWN = 6;
    private static final String NAME_DELIMITER = ", ";
    private static final String NONE = "없음";
    // 사기에서 찾을 이름으로 보지 않는 말 ("세일하는 재료 있어?", "밀키트 사고 싶어")
    private static final Set<String> SHOP_GENERIC_NAMES = Set.of("재료", "밀키트", "상품", "식재료", "거", "것");
    // 할인 상품을 물을 때 재료 상품만 보는 말
    private static final String INGREDIENT_WORD = "재료";

    private final ChatIntentExtractor chatIntentExtractor;
    private final RecipeService recipeService;
    private final IngredientMatcher ingredientMatcher;
    private final ChatCandidateProperties chatCandidateProperties;
    private final ChatWordProperties chatWordProperties;
    private final AllergyExclusionFinder allergyExclusionFinder;
    // 정한 의도 결과에 서비스 규칙(레시피 자세히 대상, 요리 이름 확인, 맛 규칙)을 적용한다
    private final IntentCorrector intentCorrector;
    // Jev 호출 (시작·기다리기·결정 로그)
    private final JevIntentRunner jevIntentRunner;
    // Jev 판단으로 의도 결과를 만든다
    private final JevIntentResolver jevIntentResolver;

    @Autowired
    public ChatIntentFacade(ChatIntentExtractor chatIntentExtractor, RecipeService recipeService, IngredientMatcher ingredientMatcher,
                            ChatCandidateProperties chatCandidateProperties, ChatWordProperties chatWordProperties,
                            AllergyExclusionFinder allergyExclusionFinder, IntentCorrector intentCorrector,
                            JevIntentRunner jevIntentRunner, JevIntentResolver jevIntentResolver) {
        this.chatIntentExtractor = chatIntentExtractor;
        this.recipeService = recipeService;
        this.ingredientMatcher = ingredientMatcher;
        this.chatCandidateProperties = chatCandidateProperties;
        this.chatWordProperties = chatWordProperties;
        this.allergyExclusionFinder = allergyExclusionFinder;
        this.intentCorrector = intentCorrector;
        this.jevIntentRunner = jevIntentRunner;
        this.jevIntentResolver = jevIntentResolver;
    }

    // 상담 문맥 없이 (첫 질문과 같게) 정한다
    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        return resolve(memberId, sessionId, request, ChatContext.empty());
    }

    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatContext context) {
        Decision decision = decide(memberId, sessionId, request, context);
        ChatIntentResolution resolution = decision.resolution();
        // 추천이면 이 상담에서 말한 알레르기·못 먹는 재료를 뺄 재료에 더한다 (AI가 놓쳐도, 이어짐·새 추천 모두. 콕 집은 요리여도 뺀다)
        if (resolution.recommends()) {
            AllergyExclusionFinder.Allergens allergens = allergyExclusionFinder.find(sessionId, request.content());
            if (!allergens.isEmpty()) {
                log.info("상담 알레르기 재료를 뺄 재료로 - memberId: {}, sessionId: {}, 재료: {}, 뺄 재료 수: {}",
                        memberId, sessionId, allergens.names(), allergens.ingredientIds().size());
                resolution = resolution.withAllergens(allergens.names(), allergens.ingredientIds());
            }
        }
        log.info("상담 의도 - memberId: {}, sessionId: {}, 의도: {}, 이어짐: {}, 출처: {}, 조건: {}, 지울 조건(removed): {}, 대상 레시피: {}, 질문 종류: {}",
                memberId, sessionId, resolution.intent(), resolution.follows(), resolution.source(), summarize(resolution.recipeQuery()),
                decision.removed(), resolution.targetRecipeId(), resolution.detailQuestion());
        logJevDecision(memberId, sessionId, request, resolution, decision.jev(), decision.jevDecision());
        return resolution;
    }

    // Jev 판단을 시작했으면 끝난 뒤 최종 결과와 결정 로그를 남긴다. 기다리지 않고, 실패해도 상담 결과에 영향이 없다
    private void logJevDecision(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatIntentResolution resolution,
                                JevIntentRunner.Run jev, JevDecision jevDecision) {
        if (jev == null) {
            return;
        }
        try {
            // 밀키트만 보여 줄 추천인지 (ChatRecommendationFacade와 같은 기준)
            boolean mealkit = resolution.recommends()
                    && MealkitWordChecker.check(request.content(), chatWordProperties.mealkit(), chatWordProperties.taste())
                    == MealkitWordChecker.Mode.ONLY;
            jevIntentRunner.logWhenDone(jev, memberId, sessionId, request.content().length(), resolution, mealkit, jevDecision);
        } catch (RuntimeException e) {
            log.warn("Jev 결정 로그 준비 실패 - memberId: {}, sessionId: {}, 원인: {}", memberId, sessionId, e.getClass().getSimpleName());
        }
    }

    // 정한 결과, 로그에 남길 지울 조건(removed, AI 결과), 시작한 Jev 판단(없으면 null), 결정 로그에 남길 결정
    private record Decision(ChatIntentResolution resolution, List<String> removed, JevIntentRunner.Run jev, JevDecision jevDecision) {
        private Decision(ChatIntentResolution resolution, JevIntentRunner.Run jev, JevDecision jevDecision) {
            this(resolution, List.of(), jev, jevDecision);
        }
    }

    private Decision decide(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatContext context) {
        if (request.mode() == ChatMessageMode.RECOMMEND) {
            return new Decision(ChatIntentResolution.recommend(Source.MODE,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly())), null, null);
        }

        List<String> categories;
        try {
            categories = categories();
        } catch (RuntimeException e) {
            log.warn("상담 분류 목록 읽기 실패, Jev를 부르지 못해 서버 답으로 - memberId: {}, sessionId: {}, 원인: {}",
                    memberId, sessionId, e.toString());
            return serverFallback(memberId, sessionId, request, null, JevDecision.Decision.JEV_FAILED_SERVER_FALLBACK);
        }
        String followUp = followUp(context);
        // Jev 판단을 OpenAI 꺼내기와 함께 시작한다 (여기서는 기다리지 않는다)
        JevIntentRunner.Run jev = jevIntentRunner.start(request.content(), categories, followUp);
        IntentExtraction extraction = extract(memberId, sessionId, request.content(), followUp);
        Optional<JevIntentJudgement> judged = jevIntentRunner.await(jev);
        if (judged.isEmpty()) {
            // Jev 1차가 실패하면 OpenAI 꺼내기의 자해 뜻만 예비로 본다 (Jev가 성공하면 이 값은 쓰지 않는다)
            if (extraction != null && extraction.hasSelfHarmRisk()) {
                log.info("상담 Jev 실패, OpenAI 자해 뜻으로 자해 안내 - memberId: {}, sessionId: {}", memberId, sessionId);
                return new Decision(ChatIntentResolution.declined(ChatIntent.SELF_HARM), jev,
                        JevDecision.selfHarm(JevDecision.Decision.JEV_FAILED_OPENAI_SELF_HARM, JevDecision.SelfHarmBy.OPENAI_RISK));
            }
            log.warn("상담 Jev 의도 판단 실패, 서버 답으로 - memberId: {}, sessionId: {}", memberId, sessionId);
            return serverFallback(memberId, sessionId, request, jev, JevDecision.Decision.JEV_FAILED_SERVER_FALLBACK);
        }
        // 자해는 꺼낸 값도 2차도 필요 없으므로 OpenAI 꺼내기나 Jev 2차가 실패했어도 1차 판단으로 자해 안내를 보낸다
        JevIntentJudgement.First first = judged.get().first();
        Optional<JevDecision.SelfHarmBy> selfHarmBy = selfHarmBy(first);
        if (selfHarmBy.isPresent()) {
            log.info("상담 Jev 자해 판단으로 자해 안내 - memberId: {}, sessionId: {}, 1등 의도: {}, 자해 확률: {}, 조건: {}",
                    memberId, sessionId, first.intent(), selfHarmProbability(first), selfHarmBy.get());
            return new Decision(ChatIntentResolution.declined(ChatIntent.SELF_HARM), jev,
                    JevDecision.selfHarm(JevDecision.Decision.JEV, selfHarmBy.get()));
        }
        // 2차(추천 조건)가 필요한데 실패했으면 Jev 실패로 서버 답 (자해 판단은 위에서 1차로 봤다)
        if (JevIntentClassifier.asksSecond(first.intent()) && !judged.get().hasSecond()) {
            log.warn("상담 Jev 2차 실패, 서버 답으로 - memberId: {}, sessionId: {}, 의도: {}", memberId, sessionId, first.intent());
            return serverFallback(memberId, sessionId, request, jev, JevDecision.Decision.JEV_FAILED_SERVER_FALLBACK);
        }
        if (extraction == null) {
            if (NEEDS_EXTRACTION.contains(first.intent())) {
                return serverFallback(memberId, sessionId, request, jev, JevDecision.Decision.OPENAI_FAILED_SERVER_FALLBACK);
            }
            log.info("상담 OpenAI 꺼내기 실패, 꺼낸 값이 필요 없는 의도라 그대로 - memberId: {}, sessionId: {}, 의도: {}",
                    memberId, sessionId, first.intent());
            extraction = IntentExtraction.empty();
        }

        CorrectionInput input = new CorrectionInput(memberId, sessionId, request.content(), categories, context);
        // 의도는 Jev 1등 의도(confidence와 상관없이), 꺼낸 값은 OpenAI 것으로 만들고, 서버는 서비스 규칙만 거친다
        JevIntentResolver.Resolved resolved = jevIntentResolver.resolve(input, judged.get(), extraction);
        JevDecision jevDecision = resolved.decision();
        log.info("상담 의도 Jev - memberId: {}, sessionId: {}, 결정: {}, 요청 종류: {}",
                memberId, sessionId, jevDecision.decision(), jevDecision.usedKind());
        IntentCorrector.Corrected corrected = intentCorrector.correct(input, resolved.result());
        IntentResult result = corrected.result();
        ChatIntentResolution resolution = switch (result.intent()) {
            case RECOMMEND -> result.follows()
                    ? recommendFollowing(memberId, sessionId, request, result, context)
                    : ChatIntentResolution.recommend(Source.AI, toRecipeQuery(memberId, sessionId, request, result));
            case RECIPE_DETAIL -> recipeDetail(memberId, sessionId, result, corrected.detailTarget());
            case FRIDGE -> ChatIntentResolution.fridge(Source.AI, lookupOf(request.content(), result));
            case SHOP -> ChatIntentResolution.shop(Source.AI, lookupOf(request.content(), result));
            case GENERAL -> ChatIntentResolution.general(Source.AI);
            case OFF_TOPIC, BLOCKED, SELF_HARM -> ChatIntentResolution.declined(result.intent());
        };
        return new Decision(resolution, result.removed() == null ? List.of() : result.removed(), jev, jevDecision);
    }

    // 자해 안내를 보낼 조건: Jev 1등 의도가 자해이거나 자해 확률이 기준 이상. 아니면 빈 값
    static Optional<JevDecision.SelfHarmBy> selfHarmBy(JevIntentJudgement.First first) {
        if (first.intent() == ChatIntent.SELF_HARM) {
            return Optional.of(JevDecision.SelfHarmBy.JEV_TOP_INTENT);
        }
        return selfHarmProbability(first) >= SELF_HARM_MIN_PROBABILITY
                ? Optional.of(JevDecision.SelfHarmBy.JEV_PROBABILITY) : Optional.empty();
    }

    // 자해 안내를 보낼지 (selfHarmBy가 있는지)
    static boolean selfHarm(JevIntentJudgement.First first) {
        return selfHarmBy(first).isPresent();
    }

    // Jev 의도별 확률의 자해 확률 (답에 없으면 0)
    private static double selfHarmProbability(JevIntentJudgement.First first) {
        Double probability = first.intentProbabilities() == null ? null : first.intentProbabilities().get(ChatIntent.SELF_HARM);
        return probability == null ? 0.0 : probability;
    }

    // OpenAI 꺼내기. 실패하면(오류·시간 초과·JSON 깨짐) null
    private IntentExtraction extract(Long memberId, Long sessionId, String message, String followUp) {
        try {
            return chatIntentExtractor.extract(memberId, sessionId, message, followUp).sanitize();
        } catch (RuntimeException e) {
            log.warn("상담 OpenAI 꺼내기 실패 - memberId: {}, sessionId: {}, 원인: {}", memberId, sessionId, e.toString());
            return null;
        }
    }

    // AI 실패 시 서버 답: 질문에 답하지 않고 선택 재료(없으면 냉장고 재료) 기반 추천 카드로 답한다 (카드는 ChatStreamFacade가 고른다)
    private Decision serverFallback(Long memberId, Long sessionId, ChatMessageCreateRequest request, JevIntentRunner.Run jev,
                                    JevDecision.Decision decision) {
        log.info("상담 AI 실패로 서버 답 - memberId: {}, sessionId: {}, 결정: {}, 선택 재료 수: {}",
                memberId, sessionId, decision, request.selectedIngredientIds().size());
        return new Decision(ChatIntentResolution.serverFallback(
                RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly())), jev, JevDecision.of(decision));
    }

    /**
     * 냉장고 보기·사기에서 볼 것. 범위(냉장고 보기만)는 AI 값을 쓰고, AI가 전체(ALL)로 뒀으면 서버가 단어로 정한다 (밀키트 → MEALKIT, 버려야·지난 → IMMINENT).
     * 이름은 AI가 뽑은 재료·상품 이름을 쓰고, 비었는데 사기이거나 냉장고 보기 범위가 전체면 서버가 말에서 재료 사전 이름을 찾는다.
     */
    private ItemLookup lookupOf(String message, IntentResult result) {
        FridgeScope scope;
        if (result.intent() == ChatIntent.SHOP) {
            scope = FridgeScope.ALL;
        } else if (result.scope() != FridgeScope.ALL) {
            scope = result.scope();
        } else {
            scope = FridgeShopWordChecker.scope(message, chatWordProperties.fridgeShop(), chatWordProperties.mealkit());
        }
        List<String> names = orEmpty(result.includeIngredients());
        // 냉장고 보기에서 "기본 양념·조미료"는 찾을 재료 이름이 아니라 범위다 ("기본 양념 뭐 있어?" → SEASONING)
        if (result.intent() == ChatIntent.FRIDGE) {
            names = names.stream().filter(name -> !FridgeShopWordChecker.seasoningWord(name, chatWordProperties.fridgeShop())).toList();
        }
        if (names.isEmpty() && scope == FridgeScope.ALL && message != null) {
            String text = message.strip().replaceAll("[?!.~…,]+$", "").strip();
            names = intentCorrector.ingredientNames(List.of(text.split("\\s+")));
        }
        if (result.intent() != ChatIntent.SHOP) {
            return new ItemLookup(scope, names);
        }
        // 사기: "재료·밀키트·상품"은 찾을 이름이 아니다 ("세일하는 재료 있어?"). 할인 말이 있으면 할인 중인 상품만, 종류는 단어로 나눈다
        List<String> itemNames = names.stream().filter(name -> !SHOP_GENERIC_NAMES.contains(ChatTexts.removeWhitespace(name))).toList();
        boolean saleOnly = FridgeShopWordChecker.asksSale(message, chatWordProperties.fridgeShop());
        return new ItemLookup(scope, itemNames, saleOnly, saleOnly ? saleProductType(message) : null);
    }

    // 할인 상품을 물을 때 종류: 밀키트 단어 → MEALKIT, "재료" → INGREDIENT, 둘 다 없거나 둘 다 있으면 null(모두)
    private ProductType saleProductType(String message) {
        boolean mealkit = MealkitWordChecker.check(message, chatWordProperties.mealkit(), chatWordProperties.taste())
                == MealkitWordChecker.Mode.ONLY;
        boolean ingredient = message != null && ChatTexts.removeWhitespace(message).contains(INGREDIENT_WORD);
        if (mealkit == ingredient) {
            return null;
        }
        return mealkit ? ProductType.MEALKIT : ProductType.INGREDIENT;
    }

    // 추천 + 이어짐: 직전 조건과 이번 말을 합친다. 직전 조건이 있을 때만 온다 (없으면 IntentCorrector가 이어짐을 아니오로 둔다)
    private ChatIntentResolution recommendFollowing(Long memberId, Long sessionId, ChatMessageCreateRequest request,
                                                    IntentResult result, ChatContext context) {
        RecipeQuery current = toRecipeQuery(memberId, sessionId, request, result);
        RecipeQuery previous = context.lastRecommendation().query().toRecipeQuery(memberId);
        Set<RefineCondition> removed = result.removedConditions();
        if (!removed.isEmpty()) {
            log.info("상담 이어짐에서 뺄 조건 - memberId: {}, sessionId: {}, 조건: {}", memberId, sessionId, removed);
        }
        RecipeQuery merged = mergeRefine(previous, current, removed);
        return ChatIntentResolution.recommendFollowing(Source.AI, followSpokenTastes(memberId, sessionId, request.content(), merged));
    }

    /**
     * 이어짐에서 맛이 부딪히면 이번 말에 나온 맛을 따른다 (맛 전체, TasteWordChecker.stances로 이번 말의 맛만 본다).
     * <ul>
     *   <li>이번 말에 원한 맛: 원하는 맛에 넣고 피할 맛에서 빼며, 그 맛과 반대인 맛(app.chat.words.taste-opposites)은 원하는 맛에서 지운다
     *       ("짭짤한 거" → "담백한 거로" → 순한맛만, "매운 거 말고" → "좀 더 매운 걸로" → 매운맛 원함·피할 맛 없음).
     *       피할 맛에 든 맛은 원하는 말이 붙었을 때만 푼다 (TasteWordChecker.surelyWanted, "매운 건 별로야"는 그대로 피함)</li>
     *   <li>이번 말에 피한 맛: 피할 맛에 넣고 원하는 맛에서 뺀다 ("매운 거 추천해줘" → "매운 거 말고")</li>
     *   <li>이번 말에 나오지 않은 맛은 직전 값을 그대로 쌓는다 ("매운 걸로" → "단 것도" → 매운맛 + 단맛)</li>
     * </ul>
     * 알레르기 재료는 맛과 상관없이 resolve에서 계속 뺀다.
     */
    RecipeQuery followSpokenTastes(Long memberId, Long sessionId, String message, RecipeQuery merged) {
        List<TasteWordChecker.TasteStance> spoken = TasteWordChecker.stances(message, chatWordProperties.taste());
        if (spoken.isEmpty()) {
            return merged;
        }
        Set<Member.Taste> wanted = new LinkedHashSet<>(merged.wantedTastes());
        Set<Member.Taste> avoid = new LinkedHashSet<>(merged.avoidTastes());
        for (TasteWordChecker.TasteStance stance : spoken) {
            if (stance.stance() == TasteWordChecker.Stance.WANT) {
                // 맛 단어만 있고 원하는 말이 없으면("매운 건 별로야") 피할 맛에 든 맛을 풀지 않는다
                if (avoid.contains(stance.taste())
                        && !TasteWordChecker.surelyWanted(message, stance.taste(), chatWordProperties.taste())) {
                    continue;
                }
                wanted.removeAll(chatWordProperties.tasteOpposites().oppositesOf(stance.taste()));
                wanted.add(stance.taste());
                avoid.remove(stance.taste());
            } else {
                avoid.add(stance.taste());
                wanted.remove(stance.taste());
            }
        }
        RecipeQuery followed = merged.withTastes(List.copyOf(wanted), List.copyOf(avoid));
        if (!followed.wantedTastes().equals(merged.wantedTastes()) || !followed.avoidTastes().equals(merged.avoidTastes())) {
            log.info("상담 이어짐 맛이 부딪혀 이번 말을 따름 - memberId: {}, sessionId: {}, 원하는 맛: {} → {}, 피할 맛: {} → {}",
                    memberId, sessionId, merged.wantedTastes(), followed.wantedTastes(), merged.avoidTastes(), followed.avoidTastes());
        }
        return followed;
    }

    /**
     * 직전 조건과 이번 말(이어짐)을 합친다.
     * <ul>
     *   <li>원하는 맛: 직전 맛에 이번 맛을 쌓고(removed에 TASTE면 이번 맛만), 부딪히는 맛은 followSpokenTastes가 이번 말을 따라 고친다</li>
     *   <li>분류·시간·키워드: AI가 [직전 조건]에 이번 말을 반영한 최종값을 낸다. AI가 비워 둔 칸은 직전 값을 쓴다
     *       ("좀 더 매운 걸로" → 맛만 바뀌고 분류·키워드는 그대로, "국물 말고 볶음으로" → 분류만 바뀜, "다른 거 없어?" → 모두 그대로).
     *       removed에 있는 조건은 직전 값을 지우고 이번 말의 값만 쓴다 ("매운 거 말고" → 맛 조건 없음, "시간 상관없이" → 시간 조건 없음)</li>
     *   <li>요리 이름·원한 표현: 이어 가지 않는다 (이어짐에서 "김피탕 레시피는 없어서…" 같은 안내가 다시 나오지 않게)</li>
     *   <li>피할 맛: 이번에 말한 피할 맛 + 직전 피할 맛을 이어 간다 ("매운 거 말고" 뒤 "다른 거 없어?"도 안 매운 걸로).
     *       이번 원하는 맛에 다시 들어간 맛("다시 매운 걸로")은 직전 피할 맛에서 풀고, 이번에 말한 피할 맛은 원하는 맛보다 앞선다</li>
     *   <li>재료: AI에 넘기지 않으므로 서버가 이어 간다. 직전 선택 재료 + 이번에 고르거나 말한 재료, 뺄 재료는 합친 뒤 선택 재료에서 뺀다.
     *       removed에 INCLUDE면 직전 선택 재료(와 선택 재료만 쓰기)를, EXCLUDE면 직전 뺄 재료를 지운다</li>
     * </ul>
     */
    static RecipeQuery mergeRefine(RecipeQuery previous, RecipeQuery current, Set<RefineCondition> removed) {
        List<Long> excludeIds = removed.contains(RefineCondition.EXCLUDE)
                ? current.excludeIngredientIds()
                : Stream.concat(previous.excludeIngredientIds().stream(), current.excludeIngredientIds().stream())
                .distinct()
                .toList();
        List<Long> selected = removed.contains(RefineCondition.INCLUDE)
                ? current.selectedIngredientIds()
                : Stream.concat(previous.selectedIngredientIds().stream(), current.selectedIngredientIds().stream()).toList();
        List<Long> selectedIds = selected.stream()
                .distinct()
                .filter(id -> !excludeIds.contains(id))
                .toList();
        boolean useSelectedOnly = removed.contains(RefineCondition.INCLUDE)
                ? current.useSelectedOnly()
                : previous.useSelectedOnly() || current.useSelectedOnly();
        // 원하는 맛은 직전 맛에 이번 맛을 쌓는다 (맛을 지우라는 말이면 이번 맛만). 부딪히는 맛은 followSpokenTastes가 이번 말을 따라 고친다
        List<Member.Taste> wanted = (removed.contains(RefineCondition.TASTE)
                ? current.wantedTastes()
                : Stream.concat(previous.wantedTastes().stream(), current.wantedTastes().stream()).distinct().toList())
                .stream()
                .filter(taste -> !current.avoidTastes().contains(taste))
                .toList();
        List<Member.Taste> avoid = Stream.concat(current.avoidTastes().stream(),
                        previous.avoidTastes().stream().filter(taste -> !wanted.contains(taste)))
                .distinct()
                .toList();
        return new RecipeQuery(
                previous.memberId(),
                selectedIds,
                wanted,
                merged(current.moodCategories(), previous.moodCategories(), removed.contains(RefineCondition.CATEGORY)),
                current.maxMinutes() != null || removed.contains(RefineCondition.MAX_MINUTES) ? current.maxMinutes() : previous.maxMinutes(),
                excludeIds,
                useSelectedOnly,
                merged(current.keywords(), previous.keywords(), removed.contains(RefineCondition.KEYWORD)),
                null,
                null
        ).withAvoidTastes(avoid);
    }

    // 뺄 조건이면 이번 값만, 아니면 이번 값이 비었을 때 직전 값
    private static <T> List<T> merged(List<T> current, List<T> previous, boolean removed) {
        return removed || !current.isEmpty() ? current : previous;
    }

    // 레시피 자세히. 이름도 번호도 맞지 않으면 일반 답 (이름만 못 찾았으면 decide에서 그 요리 추천으로 이미 바꿨다)
    private ChatIntentResolution recipeDetail(Long memberId, Long sessionId, IntentResult result, Long recipeId) {
        if (recipeId == null) {
            log.info("상담 레시피 자세히 대상을 못 찾아 일반 답으로 - memberId: {}, sessionId: {}, 번호: {}, 이름: {}",
                    memberId, sessionId, result.targetIndex(), result.targetName());
            return ChatIntentResolution.general(Source.AI);
        }
        return ChatIntentResolution.recipeDetail(Source.AI, recipeId, result.question());
    }

    /**
     * 의도 판단에 별도 메시지로 넘길 직전 추천 정보. 직전 카드가 없으면(첫 질문) null이라 프롬프트는 지금 크기 그대로다.
     * <pre>
     * [직전 추천] 1. 탕수육 / 2. 깐풍기 / 3. 돼지고기 찹쌀부침
     * [이전 추천] 두부조림, 버섯전
     * [직전 조건] 원하는 맛 SPICY / 분류 튀김 / 키워드 김피탕, 탕수육 / 요리 이름 김피탕
     * </pre>
     * [이전 추천]은 최근 대화의 다른 카드(최대 6개), [직전 조건]은 Redis에 직전 추천이 있을 때만 넣는다.
     */
    static String followUp(ChatContext context) {
        if (!context.hasLastCards()) {
            return null;
        }
        StringBuilder text = new StringBuilder("[직전 추천] ");
        List<CardName> lastCards = context.lastCards();
        for (int i = 0; i < lastCards.size(); i++) {
            text.append(i == 0 ? "" : " / ").append(i + 1).append(". ").append(lastCards.get(i).name());
        }
        Set<Long> lastIds = lastCards.stream().map(CardName::recipeId).collect(Collectors.toSet());
        List<String> earlier = context.recentMessages().stream()
                .flatMap(message -> message.cards().stream())
                .filter(card -> !lastIds.contains(card.recipeId()))
                .map(CardName::name)
                .distinct()
                .limit(EARLIER_CARDS_SHOWN)
                .toList();
        if (!earlier.isEmpty()) {
            text.append("\n[이전 추천] ").append(String.join(NAME_DELIMITER, earlier));
        }
        LastRecommendation last = context.lastRecommendation();
        if (last != null) {
            text.append("\n[직전 조건] ").append(conditionLine(last.query()));
        }
        return text.toString();
    }

    // 직전 조건 한 줄. 맛은 응답 값과 같은 이름(SPICY)으로 쓴다. 재료 조건은 서버가 이어 가므로 넣지 않는다
    private static String conditionLine(QuerySnapshot query) {
        List<String> parts = new ArrayList<>();
        if (!query.wantedTastes().isEmpty()) {
            parts.add("원하는 맛 " + query.wantedTastes().stream().map(Enum::name).collect(Collectors.joining(NAME_DELIMITER)));
        }
        if (query.avoidTastes() != null && !query.avoidTastes().isEmpty()) {
            parts.add("피할 맛 " + query.avoidTastes().stream().map(Enum::name).collect(Collectors.joining(NAME_DELIMITER)));
        }
        if (!query.moodCategories().isEmpty()) {
            parts.add("분류 " + String.join(NAME_DELIMITER, query.moodCategories()));
        }
        if (query.maxMinutes() != null) {
            parts.add("최대 " + query.maxMinutes() + "분");
        }
        if (!query.keywords().isEmpty()) {
            parts.add("키워드 " + String.join(NAME_DELIMITER, query.keywords()));
        }
        // 요리 이름·원한 표현은 이어짐에서 이어 가지 않으므로 넘기지 않는다
        return parts.isEmpty() ? NONE : String.join(" / ", parts);
    }

    // Jev가 고를 수 있는 레시피 분류: 레시피에 있는 분류(레시피 분류 캐시)에서 후보 제외 분류와 "기타"를 뺀 것
    private List<String> categories() {
        Set<String> excluded = Set.copyOf(chatCandidateProperties.excludedCategories());
        return recipeService.getClassifications().categories().stream()
                .filter(category -> !excluded.contains(category) && !PROMPT_EXCLUDED_CATEGORIES.contains(category))
                .toList();
    }

    /**
     * 추천 조건. 키워드는 [요리 이름] + AI 키워드 중 남은 것으로 합친다 (IntentResult.searchKeywords).
     * 비슷한 요리는 여기서 정하지 않고, 후보 검색에서 요리 이름 그대로 맞은 레시피가 없을 때 따로 묻는다 (SimilarDishFinder).
     */
    private RecipeQuery toRecipeQuery(Long memberId, Long sessionId, ChatMessageCreateRequest request, IntentResult result) {
        List<String> includeNames = orEmpty(result.includeIngredients());
        List<String> excludeNames = orEmpty(result.excludeIngredients());
        Map<String, Long> idsByName = matchIngredientIds(memberId, sessionId,
                Stream.concat(includeNames.stream(), excludeNames.stream()).distinct().toList());

        List<Long> excludeIds = toIds(excludeNames, idsByName);
        List<Long> selectedIds = Stream.concat(request.selectedIngredientIds().stream(), toIds(includeNames, idsByName).stream())
                .distinct()
                .filter(id -> !excludeIds.contains(id))
                .toList();

        boolean useSelectedOnly = request.useSelectedOnly() || Boolean.TRUE.equals(result.useSelectedOnly());
        // 피할 맛이 원하는 맛과 겹치면 피할 맛이 이긴다 (withAvoidTastes가 원하는 맛에서 뺀다)
        return new RecipeQuery(
                memberId,
                selectedIds,
                result.tastes(),
                result.moodCategories(),
                result.maxMinutes(),
                excludeIds,
                useSelectedOnly,
                result.searchKeywords(),
                result.dishText(),
                result.preferenceText()
        ).withAvoidTastes(result.avoidedTastes());
    }

    // 재료 이름 → 재료 ID. 못 찾은 이름(ING_CUSTOM)은 뺀다. 매칭이 실패하면(DB·검색 장애) 재료 조건 없이 계속한다
    private Map<String, Long> matchIngredientIds(Long memberId, Long sessionId, List<String> names) {
        if (names.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Long> ids = new HashMap<>();
            ingredientMatcher.matchAll(names).forEach((name, ingredient) -> {
                if (ingredient != null && !ingredient.isCustom()) {
                    ids.put(name, ingredient.getId());
                }
            });
            return ids;
        } catch (RuntimeException e) {
            log.warn("상담 재료 이름 매칭 실패, 재료 조건 없이 계속 - memberId: {}, sessionId: {}, 이름: {}, 원인: {}",
                    memberId, sessionId, names, e.toString());
            return Map.of();
        }
    }

    private static List<Long> toIds(List<String> names, Map<String, Long> idsByName) {
        return names.stream()
                .map(idsByName::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String summarize(RecipeQuery query) {
        if (query == null) {
            return "-";
        }
        return "선택 재료=" + query.selectedIngredientIds()
                + ", 원하는 맛=" + query.wantedTastes()
                + ", 피할 맛=" + query.avoidTastes()
                + ", 분류=" + query.moodCategories()
                + ", 최대 시간=" + query.maxMinutes()
                + ", 제외 재료=" + query.excludeIngredientIds()
                + ", 선택 재료만=" + query.useSelectedOnly()
                + ", 키워드=" + query.keywords()
                + ", 요리 이름=" + query.keywordText()
                // 원한 표현은 메시지 전체일 수 있어(넓은 요청) 길이만 남긴다
                + ", 원한 표현 길이=" + (query.preferenceText() == null ? 0 : query.preferenceText().length());
    }
}

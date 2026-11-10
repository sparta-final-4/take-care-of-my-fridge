package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatAskWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCategoryWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatFoodWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatFridgeShopWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteOppositeProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatMealkitWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.ItemLookup;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation.QuerySnapshot;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RefineCondition;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
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
 *   <li>mode=AUTO: AI로 의도·조건을 뽑는다. 선택 재료만 쓰기는 요청값 OR AI 결과</li>
 *   <li>AI 실패(시간 초과·오류·JSON 깨짐): 선택 재료가 있으면 추천, 없으면 일반. 에러로 끝내지 않는다</li>
 * </ul>
 * 말한 재료 이름은 IngredientMatcher로 재료 ID로 바꾸고, 못 찾은 이름(ING_CUSTOM)은 버린다.
 * 말한 재료(includeIngredients)는 고른 재료와 합쳐 선택 재료로 쓴다 (중복 제거, 빼달라고 한 재료는 뺀다).
 * 그래서 "버섯 두부로 할 수 있는"처럼 말로만 재료를 정해도 칩으로 고른 것과 같은 규칙(선택 재료만 쓰기, 2개 이상 다 쓰면 보유 비율 예외)이 적용된다.
 * <p>
 * 직전 추천 카드가 있으면 의도 판단에 [직전 추천]·[이전 추천]·[직전 조건]을 별도 메시지로 넘겨 이어지는 말을 알아듣는다.
 * <ul>
 *   <li>REFINE(다시 추천): 직전 조건이 없으면(Redis 만료·장애) 이번 말만으로 새 추천</li>
 *   <li>RECIPE_DETAIL(레시피 자세히): 이름이 직전 카드에 있으면 그 카드, 다음은 직전 카드 번호, 다음은 상담 전체 추천에서 이름으로 찾는다.
 *       못 찾으면 일반 답(이전 대화를 보고 AI가 되묻는다)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatIntentFacade {

    // AI가 고를 분류 목록에서만 뺄 분류. 뜻이 없어 조건으로 쓸 수 없다 (레시피 후보에서는 빼지 않는다)
    private static final Set<String> PROMPT_EXCLUDED_CATEGORIES = Set.of("기타");
    // 의도 판단에 넘길 [이전 추천] 이름 수
    private static final int EARLIER_CARDS_SHOWN = 6;
    // 레시피 자세히 대상을 이름으로 찾을 상담 전체 추천 수 (최근 추천부터)
    private static final int SESSION_RECIPES_SEARCHED = 30;
    private static final String NAME_DELIMITER = ", ";
    private static final String NONE = "없음";
    // 이름만 말한 말로 볼 최대 글자 수 (공백·끝 문장 부호 뺀 글). "닭가슴살 브로콜리 만두"(11자)·"매운 국물 밀키트"(7자)가 들어간다
    static final int NAME_ONLY_MAX_LENGTH = 15;
    // 사기에서 찾을 이름으로 보지 않는 말 ("세일하는 재료 있어?", "밀키트 사고 싶어")
    private static final Set<String> SHOP_GENERIC_NAMES = Set.of("재료", "밀키트", "상품", "식재료", "거", "것");
    // 할인 상품을 물을 때 재료 상품만 보는 말
    private static final String INGREDIENT_WORD = "재료";

    private final ChatIntentExtractor chatIntentExtractor;
    private final RecipeService recipeService;
    private final IngredientMatcher ingredientMatcher;
    private final ChatCandidateProperties chatCandidateProperties;
    private final ChatMessageService chatMessageService;
    private final ChatTasteWordProperties chatTasteWordProperties;
    private final ChatMealkitWordProperties chatMealkitWordProperties;
    private final ChatAskWordProperties chatAskWordProperties;
    private final ChatCategoryWordProperties chatCategoryWordProperties;
    private final ChatFoodWordProperties chatFoodWordProperties;
    private final AllergyExclusionFinder allergyExclusionFinder;
    private final ChatTasteOppositeProperties chatTasteOppositeProperties;
    private final ChatFridgeShopWordProperties chatFridgeShopWordProperties;
    // 보정마다 켜고 끄기 (기본 모두 켜짐). 평가로 AI만으로 충분한지 비교할 때 끈다
    private final ChatCorrectionProperties corrections;

    // 상담 문맥 없이 (첫 질문과 같게) 정한다
    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        return resolve(memberId, sessionId, request, ChatContext.empty());
    }

    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatContext context) {
        Decision decision = decide(memberId, sessionId, request, context);
        ChatIntentResolution resolution = decision.resolution();
        // 추천이면 이 상담에서 말한 알레르기·못 먹는 재료를 뺄 재료에 더한다 (AI가 놓쳐도, 다시 추천·새 추천 모두. 콕 집은 요리여도 뺀다)
        if (resolution.recommends()) {
            AllergyExclusionFinder.Allergens allergens = allergyExclusionFinder.find(sessionId, request.content());
            if (!allergens.isEmpty()) {
                log.info("상담 알레르기 재료를 뺄 재료로 - memberId: {}, sessionId: {}, 재료: {}, 뺄 재료 수: {}",
                        memberId, sessionId, allergens.names(), allergens.ingredientIds().size());
                resolution = resolution.withAllergens(allergens.names(), allergens.ingredientIds());
            }
        }
        log.info("상담 의도 - memberId: {}, sessionId: {}, 의도: {}, 출처: {}, 조건: {}, 지울 조건(removed): {}, 대상 레시피: {}, 질문 종류: {}",
                memberId, sessionId, resolution.intent(), resolution.source(), summarize(resolution.recipeQuery()),
                decision.removed(), resolution.targetRecipeId(), resolution.detailQuestion());
        return resolution;
    }

    // 정한 결과와 로그에 남길 지울 조건(removed, AI 결과)
    private record Decision(ChatIntentResolution resolution, List<String> removed) {
        private Decision(ChatIntentResolution resolution) {
            this(resolution, List.of());
        }
    }

    private Decision decide(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatContext context) {
        if (request.mode() == ChatMessageMode.RECOMMEND) {
            return new Decision(ChatIntentResolution.recommend(Source.MODE,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly())));
        }

        IntentResult result;
        List<String> categories;
        try {
            categories = categories();
            IntentResult raw = chatIntentExtractor.extract(memberId, sessionId, request.content(), categories, followUp(context));
            log.debug("상담 의도 AI 원본 - memberId: {}, sessionId: {}, 의도: {}, 원하는 맛: {}, 피할 맛: {}, removed: {}",
                    memberId, sessionId, raw.intent(), raw.wantedTastes(), raw.avoidTastes(), raw.removed());
            result = raw.sanitize(categories);
        } catch (RuntimeException e) {
            log.warn("상담 의도 뽑기 실패, 선택 재료 유무로 정함 - memberId: {}, sessionId: {}, 선택 재료 수: {}, 원인: {}",
                    memberId, sessionId, request.selectedIngredientIds().size(), e.toString());
            if (request.selectedIngredientIds().isEmpty()) {
                return new Decision(ChatIntentResolution.general(Source.FALLBACK));
            }
            // 선택 재료가 있어도 "두부 얼마나 보관돼?"처럼 묻는 말이면 추천으로 바꾸지 않는다
            if (AskWordChecker.asks(request.content(), chatAskWordProperties)) {
                log.info("상담 의도 뽑기 실패, 묻는 말이라 추천으로 바꾸지 않음 - memberId: {}, sessionId: {}", memberId, sessionId);
                return new Decision(ChatIntentResolution.general(Source.FALLBACK));
            }
            return new Decision(ChatIntentResolution.recommend(Source.FALLBACK,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly())));
        }

        // AI가 상관없는 질문이라고 해도 음식 말이 있으면 거절하지 않는다 (정상 질문을 거절하는 게 상관없는 질문을 통과시키는 것보다 나쁘다).
        // 되살린 GENERAL·RECOMMEND는 아래에서 AI가 처음부터 그렇게 본 것처럼 처리한다. BLOCKED·SELF_HARM은 되살리지 않는다
        if (result.intent() == ChatIntent.OFF_TOPIC && corrections.offTopicRevive()) {
            result = reviveOffTopic(memberId, sessionId, request.content(), categories, result);
        }
        // 레시피 자세히인데 물어본 레시피가 이 상담 추천에 없으면, 그 요리 이름으로 새 추천을 한다 (아래 맛·분류 보정을 거치게 여기서 바꾼다)
        Long detailTarget = null;
        if (result.intent() == ChatIntent.RECIPE_DETAIL) {
            detailTarget = findTarget(sessionId, result, context);
            if (detailTarget == null && result.targetName() != null) {
                log.info("상담 레시피 자세히 대상이 이 상담 추천에 없어 그 요리로 추천 - memberId: {}, sessionId: {}, 이름: {}",
                        memberId, sessionId, result.targetName());
                result = result.withNamed(result.includeIngredients(), List.of(result.targetName()));
            }
        }
        // 냉장고 보기 말이면 AI가 추천·일반 대화로 봐도(되살린 것 포함) 냉장고 보기로 바꾼다. 아래 밀키트·이름만 말하기 규칙보다 먼저 본다
        if (corrections.fridgeShopWords()) {
            result = applyFridgeShopWords(memberId, sessionId, request.content(), result);
        }
        boolean mealkitRecommend = corrections.mealkitRequest() && result.intent() == ChatIntent.GENERAL
                && mealkitRequested(memberId, sessionId, request.content());
        // "국물 밀키트", "김치찌개", "두부"처럼 이름만 말한 짧은 말은 보여 달라는 뜻으로 보고 추천으로 바꾼다
        if (corrections.nameOnly() && result.intent() == ChatIntent.GENERAL && !mealkitRecommend) {
            Optional<IntentResult> named = nameOnly(memberId, sessionId, request.content(), categories, result);
            if (named.isPresent()) {
                result = named.get();
            }
        }
        // 맛·분류 단어로 조건을 고치는 것은 추천·다시 추천일 때만 한다. 일반 답·레시피 자세히는 AI 결과를 그대로 쓴다
        if (result.intent() == ChatIntent.RECOMMEND || result.intent() == ChatIntent.REFINE || mealkitRecommend) {
            // 요리 이름은 AI가 아니라 서버가 정한다 (첫 키워드가 메시지에 그대로 있을 때만)
            if (corrections.dishFromKeywords()) {
                result = result.withDishFromKeywords(request.content());
            }
            if (corrections.keepOtherTastes()) {
                result = keepOtherTastes(memberId, sessionId, request.content(), result);
            }
            if (corrections.tasteWords()) {
                result = correctTastes(memberId, sessionId, request.content(), result);
            }
            if (corrections.categoryWords()) {
                result = correctCategories(memberId, sessionId, request.content(), categories, context, result);
            }
        }
        // 새 추천은 앞 대화의 맛을 잇지 않는다 (앞 조건 이어 가기는 다시 추천만). 직전 조건에서 따라온 맛만 이번 말에 없으면 뺀다
        if ((result.intent() == ChatIntent.RECOMMEND || mealkitRecommend) && corrections.spokenTastesOnly()) {
            result = keepSpokenTastes(memberId, sessionId, request.content(), context, result);
        }
        ChatIntentResolution resolution = switch (result.intent()) {
            case RECOMMEND -> ChatIntentResolution.recommend(Source.AI, toRecipeQuery(memberId, sessionId, request, result));
            case REFINE -> refine(memberId, sessionId, request, result, context);
            case RECIPE_DETAIL -> recipeDetail(memberId, sessionId, result, detailTarget);
            case FRIDGE -> ChatIntentResolution.fridge(Source.AI, lookupOf(request.content(), result));
            case SHOP -> ChatIntentResolution.shop(Source.AI, lookupOf(request.content(), result));
            case GENERAL -> mealkitRecommend
                    ? ChatIntentResolution.recommend(Source.AI, toRecipeQuery(memberId, sessionId, request, result))
                    : ChatIntentResolution.general(Source.AI);
            // 이름만 말하기·밀키트처럼 의도를 바꾸는 규칙은 GENERAL에만 걸리므로 그대로 둔다
            case OFF_TOPIC, BLOCKED, SELF_HARM -> ChatIntentResolution.declined(result.intent());
        };
        return new Decision(resolution, result.removed() == null ? List.of() : result.removed());
    }

    /**
     * AI가 상관없는 질문(OFF_TOPIC)이라고 본 말에 음식 말이 있으면 되살린다. 달라는 말이 있으면 RECOMMEND, 없으면 GENERAL.
     * <ul>
     *   <li>음식 말(app.chat.food-words, 공백 빼고) → 분류 단어(묶음 말 포함) → 재료 사전 이름 → 레시피 이름 순으로 보고, 앞에서 찾으면 뒤는 보지 않는다</li>
     *   <li>예) "주식으로 먹을 거 추천해줘" → RECOMMEND, "장보기 목록 짜줘" → GENERAL, "주식 추천해줘"·"파이썬 코드 짜줘" → OFF_TOPIC 그대로</li>
     *   <li>재료·레시피 이름 찾기가 실패하면(DB·검색 장애) 찾지 못한 것으로 본다</li>
     * </ul>
     */
    IntentResult reviveOffTopic(Long memberId, Long sessionId, String message, List<String> categories, IntentResult result) {
        Optional<String> found = foodMention(message, categories);
        if (found.isEmpty()) {
            return result;
        }
        ChatIntent intent = AskWordChecker.requests(message, chatAskWordProperties) ? ChatIntent.RECOMMEND : ChatIntent.GENERAL;
        log.info("상담 음식 말이 있어 상관없는 질문을 되살림 - memberId: {}, sessionId: {}, 찾은 것: {}, 의도: {}",
                memberId, sessionId, found.get(), intent);
        return result.withIntent(intent);
    }

    /**
     * 냉장고 보기·사기 말(app.chat.fridge-shop-words)로 의도를 한 번 더 정한다 (FridgeShopWordChecker).
     * <ul>
     *   <li>AI가 추천·일반 대화로 본 말에 냉장고 보기 말이 있으면 FRIDGE ("밀키트 뭐 남았어", "두부 아직 있어?")</li>
     *   <li>냉장고 보기 말이 없고 사기 말이 있으면 SHOP ("당근 사고 싶어", "대파 장바구니에 담아줘")</li>
     *   <li>묻는 말("어떻게 해동해?", "먹어도 돼?")이나 요리 말("뭐 먹지", "요리 추천")이 있으면 바꾸지 않는다</li>
     *   <li>AI가 냉장고 보기·사기로 봤어도 요리 말이 있으면 추천으로 둔다 ("두부 있으면 두부 요리 추천해줘")</li>
     *   <li>AI가 냉장고 보기·사기로 봤어도 묻는 말(fridge-shop-words.general)이 있으면 일반 답으로 ("유통기한 지난 거 먹어도 돼?", "주문한 거 언제 와?")</li>
     * </ul>
     * 다시 추천·레시피 자세히·거절 의도는 건드리지 않는다. 바꿨으면 INFO로 남긴다.
     */
    IntentResult applyFridgeShopWords(Long memberId, Long sessionId, String message, IntentResult result) {
        ChatIntent intent = result.intent();
        if ((intent == ChatIntent.FRIDGE || intent == ChatIntent.SHOP)
                && FridgeShopWordChecker.cooks(message, chatFridgeShopWordProperties)) {
            log.info("상담 요리 말이 있어 {}를 추천으로 - memberId: {}, sessionId: {}", intent, memberId, sessionId);
            return result.withIntent(ChatIntent.RECOMMEND);
        }
        if ((intent == ChatIntent.FRIDGE || intent == ChatIntent.SHOP)
                && FridgeShopWordChecker.asksGeneral(message, chatFridgeShopWordProperties)) {
            log.info("상담 묻는 말이 있어 {}를 일반 답으로 - memberId: {}, sessionId: {}", intent, memberId, sessionId);
            return result.withIntent(ChatIntent.GENERAL);
        }
        if (intent != ChatIntent.RECOMMEND && intent != ChatIntent.GENERAL) {
            return result;
        }
        Optional<ChatIntent> changed = FridgeShopWordChecker.check(message, chatFridgeShopWordProperties, chatAskWordProperties);
        if (changed.isEmpty()) {
            return result;
        }
        log.info("상담 냉장고 보기 말이 있어 의도를 바꿈 - memberId: {}, sessionId: {}, 의도: {} → {}",
                memberId, sessionId, intent, changed.get());
        return result.withIntent(changed.get());
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
            scope = FridgeShopWordChecker.scope(message, chatFridgeShopWordProperties, chatMealkitWordProperties);
        }
        List<String> names = orEmpty(result.includeIngredients());
        // 냉장고 보기에서 "기본 양념·조미료"는 찾을 재료 이름이 아니라 범위다 ("기본 양념 뭐 있어?" → SEASONING)
        if (result.intent() == ChatIntent.FRIDGE) {
            names = names.stream().filter(name -> !FridgeShopWordChecker.seasoningWord(name, chatFridgeShopWordProperties)).toList();
        }
        if (names.isEmpty() && scope == FridgeScope.ALL && message != null) {
            String text = message.strip().replaceAll("[?!.~…,]+$", "").strip();
            names = ingredientNames(List.of(text.split("\\s+")));
        }
        if (result.intent() != ChatIntent.SHOP) {
            return new ItemLookup(scope, names);
        }
        // 사기: "재료·밀키트·상품"은 찾을 이름이 아니다 ("세일하는 재료 있어?"). 할인 말이 있으면 할인 중인 상품만, 종류는 단어로 나눈다
        List<String> itemNames = names.stream().filter(name -> !SHOP_GENERIC_NAMES.contains(compact(name))).toList();
        boolean saleOnly = FridgeShopWordChecker.asksSale(message, chatFridgeShopWordProperties);
        return new ItemLookup(scope, itemNames, saleOnly, saleOnly ? saleProductType(message) : null);
    }

    // 할인 상품을 물을 때 종류: 밀키트 단어 → MEALKIT, "재료" → INGREDIENT, 둘 다 없거나 둘 다 있으면 null(모두)
    private ProductType saleProductType(String message) {
        boolean mealkit = MealkitWordChecker.check(message, chatMealkitWordProperties, chatTasteWordProperties)
                == MealkitWordChecker.Mode.ONLY;
        boolean ingredient = message != null && compact(message).contains(INGREDIENT_WORD);
        if (mealkit == ingredient) {
            return null;
        }
        return mealkit ? ProductType.MEALKIT : ProductType.INGREDIENT;
    }

    // 보낸 말에 있는 음식 말의 종류 (음식 말·분류·재료·레시피 이름). 로그에는 종류만 남긴다
    private Optional<String> foodMention(String message, List<String> categories) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String compact = message.replaceAll("\\s+", "");
        if (chatFoodWordProperties.food().stream().map(word -> word.replaceAll("\\s+", ""))
                .anyMatch(word -> !word.isEmpty() && compact.contains(word))) {
            return Optional.of("음식 말");
        }
        if (!CategoryWordChecker.check(message, categories, chatTasteWordProperties, chatCategoryWordProperties.groups()).isEmpty()) {
            return Optional.of("분류");
        }
        String text = message.strip().replaceAll("[?!.~…,]+$", "").strip();
        if (!ingredientNames(List.of(text.split("\\s+"))).isEmpty()) {
            return Optional.of("재료");
        }
        try {
            if (!recipeService.suggestNames(text, 1).isEmpty()) {
                return Optional.of("레시피 이름");
            }
        } catch (RuntimeException e) {
            log.warn("상담 상관없는 질문의 레시피 이름 찾기 실패, 찾지 못한 것으로 - 원인: {}", e.toString());
        }
        return Optional.empty();
    }

    /**
     * "밀키트 뭐 있어"처럼 밀키트를 달라는 말인데 AI가 일반 대화로 본 말은 추천으로 바꾼다 (밀키트 카드는 추천 답에만 붙는다).
     * 달라는 말이 있고 묻는 말이 없을 때만 바꾼다 (AskWordChecker). 밀키트와 같이 쓴 "있어·있나·없어"도 달라는 말로 본다. "밀키트 추천 기준이 뭐야?"는 묻는 말이라 일반 답 그대로다.
     * "국물 밀키트"처럼 달라는 말이 없는 짧은 말은 이름만 말한 말(nameOnly)에서 본다.
     */
    private boolean mealkitRequested(Long memberId, Long sessionId, String message) {
        boolean mealkit = MealkitWordChecker.check(message, chatMealkitWordProperties, chatTasteWordProperties)
                == MealkitWordChecker.Mode.ONLY;
        if (!mealkit) {
            return false;
        }
        if (AskWordChecker.requestsMealkit(message, chatAskWordProperties)) {
            log.info("상담 밀키트를 달라고 해 일반 대화를 추천으로 - memberId: {}, sessionId: {}", memberId, sessionId);
            return true;
        }
        if (AskWordChecker.asks(message, chatAskWordProperties)) {
            log.info("상담 밀키트를 말했지만 묻는 말이라 일반 대화 그대로 - memberId: {}, sessionId: {}", memberId, sessionId);
        }
        return false;
    }

    /**
     * 이름만 말한 짧은 말("국물 밀키트", "디저트", "두부", "김치찌개")은 그걸 보여 달라는 뜻이라 AI가 GENERAL이어도 추천으로 바꾼다.
     * <ul>
     *   <li>묻는 말이 없고(AskWordChecker), 공백·끝 문장 부호를 뺀 글이 {@value #NAME_ONLY_MAX_LENGTH}자 이하일 때만 본다</li>
     *   <li>밀키트 단어, 분류 단어(묶음 말 포함), 재료 사전 이름, 레시피 이름 중 하나가 있으면 바꾼다 (앞에서 찾으면 뒤는 보지 않는다).
     *       재료 이름이면 그 재료를 말한 재료로, 레시피 이름이면 그 이름을 요리 이름 키워드로 채운다.
     *       분류·맛은 추천 조건 보정(correctCategories·correctTastes)이 채운다</li>
     *   <li>인사 말("안녕 밥은?")이 있거나 지난 일을 묻는 말("밥 먹었어?")이면 바꾸지 않는다</li>
     *   <li>"안녕"처럼 음식 말이 없으면 그대로 일반 답이다</li>
     * </ul>
     */
    Optional<IntentResult> nameOnly(Long memberId, Long sessionId, String message, List<String> categories, IntentResult result) {
        if (message == null || AskWordChecker.asks(message, chatAskWordProperties)
                || AskWordChecker.greets(message, chatAskWordProperties)) {
            return Optional.empty();
        }
        String text = message.strip().replaceAll("[?!.~…,]+$", "").strip();
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty() || compact.length() > NAME_ONLY_MAX_LENGTH) {
            return Optional.empty();
        }
        String found = null;
        List<String> includes = List.of();
        List<String> keywords = List.of();
        if (MealkitWordChecker.check(message, chatMealkitWordProperties, chatTasteWordProperties) == MealkitWordChecker.Mode.ONLY) {
            found = "밀키트";
        } else if (CategoryWordChecker.check(message, categories, chatTasteWordProperties, chatCategoryWordProperties.groups())
                .stream().anyMatch(stance -> stance.stance() == CategoryWordChecker.Stance.WANT)) {
            found = "분류";
        } else {
            List<String> ingredients = ingredientNames(List.of(text.split("\\s+")));
            if (!ingredients.isEmpty()) {
                found = "재료";
                includes = ingredients;
            } else if (!recipeService.suggestNames(text, 1).isEmpty()) {
                found = "레시피 이름";
                keywords = List.of(text);
            }
        }
        if (found == null) {
            return Optional.empty();
        }
        log.info("상담 이름만 말해 일반 대화를 추천으로 - memberId: {}, sessionId: {}, 찾은 것: {}, 재료: {}, 키워드: {}",
                memberId, sessionId, found, includes, keywords);
        // 요리 이름은 뒤에서 첫 키워드로 정한다 (레시피 이름이면 그 이름이 첫 키워드)
        return Optional.of(result.withNamed(includes.isEmpty() ? result.includeIngredients() : includes,
                keywords.isEmpty() ? result.keywords() : keywords));
    }

    // 재료 사전에 있는 이름만 (직접 입력·밀키트 재료로 잡힌 이름은 뺀다). 매칭이 실패하면(DB·검색 장애) 빈 목록
    private List<String> ingredientNames(List<String> words) {
        try {
            List<String> names = new ArrayList<>();
            ingredientMatcher.matchAll(words).forEach((name, ingredient) -> {
                if (ingredient != null && !ingredient.isCustom() && !ingredient.isMealkit()) {
                    names.add(name);
                }
            });
            return names;
        } catch (RuntimeException e) {
            log.warn("상담 이름만 말한 말의 재료 매칭 실패, 재료 없이 - 원인: {}", e.toString());
            return List.of();
        }
    }

    /**
     * 메시지의 맛 단어로 원하는 맛·피할 맛을 한 번 더 정한다 (TasteWordChecker.stances, 다섯 맛 모두). 맛 단어가 있는 맛만 바꾸고,
     * AI 결과와 다르면 서버 판단을 쓰고 DEBUG로 남긴다. "좀 더 매운 걸로"가 피할 맛으로, "짠 거 말고"가 원하는 맛으로 가는 것을 막는다.
     * 서버가 확실할 때만 덮어쓴다: 부정어가 있으면 피할 맛으로 바꾸지만, 부정어가 없다는 것만으로는 AI가 피할 맛으로 둔 맛을
     * 원하는 맛으로 바꾸지 않는다. 원하는 말("더", "걸로", "먹고 싶어")이 붙어 있을 때만 바꾼다 (TasteWordChecker.surelyWanted).
     * 모르는 부정어("매운 건 별로야")에서 AI가 맞게 본 피할 맛을 서버가 뒤집지 않게 하기 위해서다.
     */
    IntentResult correctTastes(Long memberId, Long sessionId, String message, IntentResult result) {
        List<String> wanted = new ArrayList<>(result.wantedTastes() == null ? List.of() : result.wantedTastes());
        List<String> avoid = new ArrayList<>(result.avoidTastes() == null ? List.of() : result.avoidTastes());
        for (TasteWordChecker.TasteStance stance : TasteWordChecker.stances(message, chatTasteWordProperties)) {
            String taste = stance.taste().name();
            boolean want = stance.stance() == TasteWordChecker.Stance.WANT;
            if (want && avoid.contains(taste) && !wanted.contains(taste)
                    && !TasteWordChecker.surelyWanted(message, stance.taste(), chatTasteWordProperties)) {
                log.debug("상담 맛 단어가 애매해 AI 피할 맛을 그대로 둠 - memberId: {}, sessionId: {}, 맛: {}", memberId, sessionId, taste);
                continue;
            }
            // 이미 맞는 쪽에 있으면 순서를 건드리지 않는다
            List<String> keep = want ? wanted : avoid;
            (want ? avoid : wanted).remove(taste);
            if (!keep.contains(taste)) {
                keep.add(taste);
            }
        }
        if (sameTastes(wanted, result.wantedTastes()) && sameTastes(avoid, result.avoidTastes())) {
            return result;
        }
        log.debug("상담 맛 단어로 맛 조건 바꿈 - memberId: {}, sessionId: {}, AI 원하는 맛: {}, AI 피할 맛: {}, 바꾼 원하는 맛: {}, 바꾼 피할 맛: {}",
                memberId, sessionId, result.wantedTastes(), result.avoidTastes(), wanted, avoid);
        return result.withTastes(wanted, avoid);
    }

    /**
     * 피하는 맛만 말했으면("매운 건 빼줘") AI가 넣은 뺄 조건 TASTE를 지운다. AI가 "빼줘"를 맛 조건 지우기로 읽으면
     * 다시 추천에서 직전에 원한 다른 맛("짭짤한 거")까지 사라지기 때문이다. 피할 맛은 그대로 둔다.
     * 맛 조건을 지우라는 말("맛 상관없이", "맛은 아무거나")이 있으면 AI 값을 그대로 쓴다. 지웠으면 INFO로 남긴다.
     */
    IntentResult keepOtherTastes(Long memberId, Long sessionId, String message, IntentResult result) {
        List<String> removed = orEmpty(result.removed());
        if (!removed.contains(RefineCondition.TASTE.name()) || TasteWordChecker.clearsTastes(message, chatTasteWordProperties)) {
            return result;
        }
        boolean avoids = TasteWordChecker.stances(message, chatTasteWordProperties).stream()
                .anyMatch(stance -> stance.stance() == TasteWordChecker.Stance.AVOID);
        if (!avoids) {
            return result;
        }
        List<String> kept = removed.stream().filter(condition -> !condition.equals(RefineCondition.TASTE.name())).toList();
        log.info("상담 피하는 맛만 말해서 맛 조건 지우기를 뺌 - memberId: {}, sessionId: {}, removed: {} → {}",
                memberId, sessionId, removed, kept);
        return result.withRemoved(kept);
    }

    /**
     * 새 추천에서 앞 대화에서 따라온 맛을 뺀다: 직전 조건(Redis 문맥)의 원하는 맛·피할 맛에 있던 맛이고, 이번 말에 그 맛 단어가 없을 때만
     * (TasteWordChecker.mentioned). "매운 거 말고" 다음 "김치찌개"에서 AI가 앞의 피할 맛(매운맛)을 따라 넣어 김치찌개가 빠지는 것을 막는다.
     * 직전 조건이 없거나 직전에 없던 맛은 AI 값 그대로 둔다. 맛 단어 목록에 없는 말("맵고", "화끈한", "얼얼한")에서 AI가 맞게 넣은 맛을
     * 지우지 않기 위해서다 (평가 T11~T14). 비웠으면 INFO로 남긴다.
     */
    IntentResult keepSpokenTastes(Long memberId, Long sessionId, String message, ChatContext context, IntentResult result) {
        QuerySnapshot previous = context.lastRecommendation() == null ? null : context.lastRecommendation().query();
        if (previous == null) {
            return result;
        }
        Set<Member.Taste> carried = new java.util.HashSet<>(previous.wantedTastes());
        if (previous.avoidTastes() != null) {
            carried.addAll(previous.avoidTastes());
        }
        Set<Member.Taste> spoken = TasteWordChecker.mentioned(message, chatTasteWordProperties);
        List<String> wanted = orEmpty(result.wantedTastes()).stream().filter(taste -> !carriedOnly(carried, spoken, taste)).toList();
        List<String> avoid = orEmpty(result.avoidTastes()).stream().filter(taste -> !carriedOnly(carried, spoken, taste)).toList();
        if (wanted.size() == orEmpty(result.wantedTastes()).size() && avoid.size() == orEmpty(result.avoidTastes()).size()) {
            return result;
        }
        log.info("상담 새 추천이라 앞 대화에서 따라온 맛을 뺌 - memberId: {}, sessionId: {}, 원하는 맛: {} → {}, 피할 맛: {} → {}",
                memberId, sessionId, result.wantedTastes(), wanted, result.avoidTastes(), avoid);
        return result.withTastes(wanted, avoid);
    }

    // AI가 준 맛 이름(SPICY 등)이 직전 조건에 있었고 이번 말에는 나오지 않은 맛인지 (앞 대화에서 따라온 맛). 모르는 이름은 sanitize가 이미 걸렀다
    private static boolean carriedOnly(Set<Member.Taste> carried, Set<Member.Taste> spoken, String taste) {
        try {
            Member.Taste value = Member.Taste.valueOf(taste);
            return carried.contains(value) && !spoken.contains(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 메시지에 분류 이름이 그대로 있으면 그 분류를 쓴다 (CategoryWordChecker). AI가 비웠거나 다른 분류를 넣었어도 이번 말이 앞선다.
     * <ul>
     *   <li>원하는 분류가 있으면 분류 조건은 그 분류만. 앞 대화에서 이어진 키워드·요리 이름(이번 말에 없는 것)은 뺀다
     *       (김피탕 추천 뒤 "디저트 추천해줘"가 김피탕·튀김을 이어 가지 않게)</li>
     *   <li>원하는 분류가 직전 분류와 다르면 removed에 KEYWORD를 더한다 (다시 추천에서도 직전 키워드를 이어 가지 않게)</li>
     *   <li>피하는 분류("디저트 말고")는 분류 조건에서 빼고, 원하는 분류가 없으면 removed에 CATEGORY를 더한다 (직전 분류를 이어 가지 않게)</li>
     * </ul>
     * 바꿨으면 INFO로 남긴다 (채움: AI가 비움, 바꿈: AI와 다름).
     */
    IntentResult correctCategories(Long memberId, Long sessionId, String message, List<String> categories, ChatContext context,
                                   IntentResult result) {
        List<CategoryWordChecker.CategoryStance> stances = CategoryWordChecker.check(message, categories, chatTasteWordProperties,
                chatCategoryWordProperties.groups());
        if (stances.isEmpty()) {
            return result;
        }
        List<String> wanted = stances.stream()
                .filter(stance -> stance.stance() == CategoryWordChecker.Stance.WANT)
                .map(CategoryWordChecker.CategoryStance::category)
                .toList();
        List<String> avoided = stances.stream()
                .filter(stance -> stance.stance() == CategoryWordChecker.Stance.AVOID)
                .map(CategoryWordChecker.CategoryStance::category)
                .toList();
        List<String> aiCategories = orEmpty(result.moodCategories());
        List<String> removed = new ArrayList<>(orEmpty(result.removed()));
        List<String> keywords = result.keywords();
        String dishText = result.dishText();
        List<String> finalCategories;
        if (!wanted.isEmpty()) {
            finalCategories = wanted;
            QuerySnapshot previous = context.lastRecommendation() == null ? null : context.lastRecommendation().query();
            List<String> previousKeywords = previous == null ? List.of() : previous.keywords();
            String compactMessage = compact(message);
            // 직전 조건에서 끌려온 키워드(이번 말에 없는 것)만 뺀다. 이번 말로 AI가 늘린 키워드는 둔다
            keywords = keywords == null ? null : keywords.stream()
                    .filter(keyword -> !previousKeywords.contains(keyword) || compactMessage.contains(compact(keyword)))
                    .toList();
            dishText = dishText != null && compactMessage.contains(compact(dishText)) ? dishText : null;
            if (previous == null || !previous.moodCategories().containsAll(wanted)) {
                addOnce(removed, RefineCondition.KEYWORD.name());
            }
        } else {
            finalCategories = aiCategories.stream().filter(category -> !avoided.contains(category)).toList();
            addOnce(removed, RefineCondition.CATEGORY.name());
        }
        IntentResult corrected = result.withCategories(finalCategories, keywords, dishText, removed);
        String change = finalCategories.equals(aiCategories) ? "그대로" : aiCategories.isEmpty() ? "채움" : "바꿈";
        log.info("상담 분류 단어 확인 - memberId: {}, sessionId: {}, 분류 {}: AI {} → {}, 원하는 분류: {}, 피할 분류: {}, "
                        + "키워드: AI {} → {}, removed: {}",
                memberId, sessionId, change, aiCategories, finalCategories, wanted, avoided, result.keywords(), keywords, removed);
        return corrected;
    }

    private static void addOnce(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static boolean sameTastes(List<String> corrected, List<String> original) {
        List<String> before = original == null ? List.of() : original;
        return Set.copyOf(corrected).equals(Set.copyOf(before));
    }

    // 다시 추천: 직전 조건과 이번 말을 합친다. 직전 조건이 없으면(Redis 만료·장애, 첫 질문) 이번 말만으로 새 추천
    private ChatIntentResolution refine(Long memberId, Long sessionId, ChatMessageCreateRequest request, IntentResult result,
                                        ChatContext context) {
        RecipeQuery current = toRecipeQuery(memberId, sessionId, request, result);
        if (context.lastRecommendation() == null) {
            log.info("상담 다시 추천인데 직전 조건이 없어 새 추천으로 - memberId: {}, sessionId: {}", memberId, sessionId);
            return ChatIntentResolution.recommend(Source.AI, current);
        }
        RecipeQuery previous = context.lastRecommendation().query().toRecipeQuery(memberId);
        Set<RefineCondition> removed = result.removedConditions();
        if (!removed.isEmpty()) {
            log.info("상담 다시 추천에서 뺄 조건 - memberId: {}, sessionId: {}, 조건: {}", memberId, sessionId, removed);
        }
        RecipeQuery merged = mergeRefine(previous, current, removed);
        return ChatIntentResolution.refine(Source.AI, corrections.refineSpokenTastes()
                ? followSpokenTastes(memberId, sessionId, request.content(), merged)
                : merged);
    }

    /**
     * 다시 추천에서 맛이 부딪히면 이번 말에 나온 맛을 따른다 (맛 전체, TasteWordChecker.stances로 이번 말의 맛만 본다).
     * <ul>
     *   <li>이번 말에 원한 맛: 원하는 맛에 넣고 피할 맛에서 빼며, 그 맛과 반대인 맛(app.chat.taste-opposites)은 원하는 맛에서 지운다
     *       ("짭짤한 거" → "담백한 거로" → 순한맛만, "매운 거 말고" → "좀 더 매운 걸로" → 매운맛 원함·피할 맛 없음).
     *       피할 맛에 든 맛은 원하는 말이 붙었을 때만 푼다 (TasteWordChecker.surelyWanted, "매운 건 별로야"는 그대로 피함)</li>
     *   <li>이번 말에 피한 맛: 피할 맛에 넣고 원하는 맛에서 뺀다 ("매운 거 추천해줘" → "매운 거 말고")</li>
     *   <li>이번 말에 나오지 않은 맛은 직전 값을 그대로 쌓는다 ("매운 걸로" → "단 것도" → 매운맛 + 단맛)</li>
     * </ul>
     * 알레르기 재료는 맛과 상관없이 resolve에서 계속 뺀다.
     */
    RecipeQuery followSpokenTastes(Long memberId, Long sessionId, String message, RecipeQuery merged) {
        List<TasteWordChecker.TasteStance> spoken = TasteWordChecker.stances(message, chatTasteWordProperties);
        if (spoken.isEmpty()) {
            return merged;
        }
        Set<Member.Taste> wanted = new LinkedHashSet<>(merged.wantedTastes());
        Set<Member.Taste> avoid = new LinkedHashSet<>(merged.avoidTastes());
        for (TasteWordChecker.TasteStance stance : spoken) {
            if (stance.stance() == TasteWordChecker.Stance.WANT) {
                // 맛 단어만 있고 원하는 말이 없으면("매운 건 별로야") 피할 맛에 든 맛을 풀지 않는다 (correctTastes와 같은 기준)
                if (avoid.contains(stance.taste())
                        && !TasteWordChecker.surelyWanted(message, stance.taste(), chatTasteWordProperties)) {
                    continue;
                }
                wanted.removeAll(chatTasteOppositeProperties.oppositesOf(stance.taste()));
                wanted.add(stance.taste());
                avoid.remove(stance.taste());
            } else {
                avoid.add(stance.taste());
                wanted.remove(stance.taste());
            }
        }
        RecipeQuery followed = merged.withTastes(List.copyOf(wanted), List.copyOf(avoid));
        if (!followed.wantedTastes().equals(merged.wantedTastes()) || !followed.avoidTastes().equals(merged.avoidTastes())) {
            log.info("상담 다시 추천 맛이 부딪혀 이번 말을 따름 - memberId: {}, sessionId: {}, 원하는 맛: {} → {}, 피할 맛: {} → {}",
                    memberId, sessionId, merged.wantedTastes(), followed.wantedTastes(), merged.avoidTastes(), followed.avoidTastes());
        }
        return followed;
    }

    /**
     * 직전 조건과 이번 말(다시 추천)을 합친다.
     * <ul>
     *   <li>원하는 맛: 직전 맛에 이번 맛을 쌓고(removed에 TASTE면 이번 맛만), 부딪히는 맛은 followSpokenTastes가 이번 말을 따라 고친다</li>
     *   <li>분류·시간·키워드: AI가 [직전 조건]에 이번 말을 반영한 최종값을 낸다. AI가 비워 둔 칸은 직전 값을 쓴다
     *       ("좀 더 매운 걸로" → 맛만 바뀌고 분류·키워드는 그대로, "국물 말고 볶음으로" → 분류만 바뀜, "다른 거 없어?" → 모두 그대로).
     *       removed에 있는 조건은 직전 값을 지우고 이번 말의 값만 쓴다 ("매운 거 말고" → 맛 조건 없음, "시간 상관없이" → 시간 조건 없음)</li>
     *   <li>요리 이름·원한 표현: 이어 가지 않는다 (다시 추천에서 "김피탕 레시피는 없어서…" 같은 안내가 다시 나오지 않게)</li>
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
     * 물어본 레시피 ID. 이름이 직전 카드에 있으면 그 카드 → 직전 카드 번호 → 상담 전체 추천(최근부터)에서 이름 순으로 찾는다.
     * 상담 전체 추천은 앞에서 못 찾았을 때만 읽는다 (추천 1번, 이름 1번).
     */
    Long findTarget(Long sessionId, IntentResult result, ChatContext context) {
        List<CardName> lastCards = context.lastCards();
        String name = result.targetName();
        Optional<Long> inLast = matchName(lastCards, name);
        if (inLast.isPresent()) {
            return inLast.get();
        }
        Integer index = result.targetIndex();
        if (index != null && index <= lastCards.size()) {
            return lastCards.get(index - 1).recipeId();
        }
        if (name == null) {
            return null;
        }
        List<Long> sessionRecipeIds = chatMessageService
                .findRecentRecommendedRecipeIds(List.of(sessionId), SESSION_RECIPES_SEARCHED)
                .getOrDefault(sessionId, List.of());
        if (sessionRecipeIds.isEmpty()) {
            return null;
        }
        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(sessionRecipeIds);
        List<CardName> sessionCards = sessionRecipeIds.stream()
                .map(summaries::get)
                .filter(Objects::nonNull)
                .map(summary -> new CardName(summary.recipeId(), summary.name()))
                .toList();
        return matchName(sessionCards, name).orElse(null);
    }

    // 양쪽 모두 띄어쓰기를 빼고 한쪽 이름이 다른 쪽을 담으면 같은 레시피로 본다 ("장어 조림" = "장어조림", "깐풍기" ↔ "매콤 깐풍기")
    private static Optional<Long> matchName(List<CardName> cards, String name) {
        if (name == null) {
            return Optional.empty();
        }
        String target = compact(name);
        return cards.stream()
                .filter(card -> {
                    String cardName = compact(card.name());
                    return cardName.contains(target) || target.contains(cardName);
                })
                .map(CardName::recipeId)
                .findFirst();
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
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
        // 요리 이름·원한 표현은 다시 추천에서 이어 가지 않으므로 넘기지 않는다
        return parts.isEmpty() ? NONE : String.join(" / ", parts);
    }

    // AI가 고를 수 있는 레시피 분류: 레시피에 있는 분류(레시피 분류 캐시)에서 후보 제외 분류와 "기타"를 뺀 것
    private List<String> categories() {
        Set<String> excluded = Set.copyOf(chatCandidateProperties.excludedCategories());
        return recipeService.getClassifications().categories().stream()
                .filter(category -> !excluded.contains(category) && !PROMPT_EXCLUDED_CATEGORIES.contains(category))
                .toList();
    }

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
                result.keywords(),
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
                + ", 원한 표현=" + query.preferenceText();
    }
}

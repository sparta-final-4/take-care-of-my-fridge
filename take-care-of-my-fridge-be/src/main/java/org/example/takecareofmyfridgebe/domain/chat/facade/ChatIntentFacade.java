package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatAskWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
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
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ChatTasteWordProperties chatTasteWordProperties;
    private final ChatMealkitWordProperties chatMealkitWordProperties;
    private final ChatAskWordProperties chatAskWordProperties;
    private final AllergyExclusionFinder allergyExclusionFinder;
    private final ChatTasteOppositeProperties chatTasteOppositeProperties;
    private final ChatFridgeShopWordProperties chatFridgeShopWordProperties;
    // 다시 추천 맛 따르기(refineSpokenTastes)만 여기서 본다. 나머지 보정 켜고 끄기는 IntentCorrector가 본다
    private final ChatCorrectionProperties corrections;
    // AI 결과를 서버 단어 규칙으로 고친다
    private final IntentCorrector intentCorrector;

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

        // 서버 단어 규칙으로 고친다 (단계 순서와 의존 관계는 IntentCorrector)
        IntentCorrector.Corrected corrected = intentCorrector.correct(memberId, sessionId, request.content(), categories,
                context, result);
        result = corrected.result();
        boolean mealkitRecommend = corrected.mealkitRecommend();
        Long detailTarget = corrected.detailTarget();
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
            names = intentCorrector.ingredientNames(List.of(text.split("\\s+")));
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

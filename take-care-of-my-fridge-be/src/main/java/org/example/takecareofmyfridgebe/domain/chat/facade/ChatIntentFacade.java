package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation.QuerySnapshot;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
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

    private final ChatIntentExtractor chatIntentExtractor;
    private final RecipeService recipeService;
    private final IngredientMatcher ingredientMatcher;
    private final ChatCandidateProperties chatCandidateProperties;
    private final ChatMessageService chatMessageService;

    // 상담 문맥 없이 (첫 질문과 같게) 정한다
    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        return resolve(memberId, sessionId, request, ChatContext.empty());
    }

    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatContext context) {
        ChatIntentResolution resolution = decide(memberId, sessionId, request, context);
        log.info("상담 의도 - memberId: {}, sessionId: {}, 의도: {}, 출처: {}, 조건: {}, 대상 레시피: {}",
                memberId, sessionId, resolution.intent(), resolution.source(), summarize(resolution.recipeQuery()),
                resolution.targetRecipeId());
        return resolution;
    }

    private ChatIntentResolution decide(Long memberId, Long sessionId, ChatMessageCreateRequest request, ChatContext context) {
        if (request.mode() == ChatMessageMode.RECOMMEND) {
            return ChatIntentResolution.recommend(Source.MODE,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly()));
        }

        IntentResult result;
        try {
            List<String> categories = categories();
            result = chatIntentExtractor.extract(memberId, sessionId, request.content(), categories, followUp(context))
                    .sanitize(categories);
        } catch (RuntimeException e) {
            log.warn("상담 의도 뽑기 실패, 선택 재료 유무로 정함 - memberId: {}, sessionId: {}, 선택 재료 수: {}, 원인: {}",
                    memberId, sessionId, request.selectedIngredientIds().size(), e.toString());
            return request.selectedIngredientIds().isEmpty()
                    ? ChatIntentResolution.general(Source.FALLBACK)
                    : ChatIntentResolution.recommend(Source.FALLBACK,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly()));
        }

        return switch (result.intent()) {
            case RECOMMEND -> ChatIntentResolution.recommend(Source.AI, toRecipeQuery(memberId, sessionId, request, result));
            case REFINE -> refine(memberId, sessionId, request, result, context);
            case RECIPE_DETAIL -> recipeDetail(memberId, sessionId, result, context);
            case GENERAL -> ChatIntentResolution.general(Source.AI);
        };
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
        return ChatIntentResolution.refine(Source.AI, mergeRefine(previous, current));
    }

    /**
     * 직전 조건과 이번 말(다시 추천)을 합친다.
     * <ul>
     *   <li>맛·분류·시간·키워드·요리 이름·원한 표현: AI가 [직전 조건]에 이번 말을 반영한 최종값을 낸다. AI가 비워 둔 칸은 직전 값을 쓴다
     *       ("좀 더 매운 걸로" → 맛만 바뀌고 분류·키워드는 그대로, "국물 말고 볶음으로" → 분류만 바뀜, "다른 거 없어?" → 모두 그대로)</li>
     *   <li>재료: AI에 넘기지 않으므로 서버가 이어 간다. 직전 선택 재료 + 이번에 고르거나 말한 재료, 뺄 재료는 합친 뒤 선택 재료에서 뺀다</li>
     * </ul>
     */
    static RecipeQuery mergeRefine(RecipeQuery previous, RecipeQuery current) {
        List<Long> excludeIds = Stream.concat(previous.excludeIngredientIds().stream(), current.excludeIngredientIds().stream())
                .distinct()
                .toList();
        List<Long> selectedIds = Stream.concat(previous.selectedIngredientIds().stream(), current.selectedIngredientIds().stream())
                .distinct()
                .filter(id -> !excludeIds.contains(id))
                .toList();
        return new RecipeQuery(
                previous.memberId(),
                selectedIds,
                orPrevious(current.wantedTastes(), previous.wantedTastes()),
                orPrevious(current.moodCategories(), previous.moodCategories()),
                current.maxMinutes() != null ? current.maxMinutes() : previous.maxMinutes(),
                excludeIds,
                previous.useSelectedOnly() || current.useSelectedOnly(),
                orPrevious(current.keywords(), previous.keywords()),
                current.keywordText() != null ? current.keywordText() : previous.keywordText(),
                current.preferenceText() != null ? current.preferenceText() : previous.preferenceText()
        );
    }

    private static <T> List<T> orPrevious(List<T> current, List<T> previous) {
        return current.isEmpty() ? previous : current;
    }

    // 레시피 자세히. 물어본 레시피를 못 찾으면 일반 답
    private ChatIntentResolution recipeDetail(Long memberId, Long sessionId, IntentResult result, ChatContext context) {
        Long recipeId = findTarget(sessionId, result, context);
        if (recipeId == null) {
            log.info("상담 레시피 자세히 대상을 못 찾아 일반 답으로 - memberId: {}, sessionId: {}, 번호: {}, 이름: {}",
                    memberId, sessionId, result.targetIndex(), result.targetName());
            return ChatIntentResolution.general(Source.AI);
        }
        return ChatIntentResolution.recipeDetail(Source.AI, recipeId);
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

    // 띄어쓰기를 빼고 한쪽 이름이 다른 쪽을 담으면 같은 레시피로 본다 ("깐풍기" ↔ "매콤 깐풍기")
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
        if (!query.moodCategories().isEmpty()) {
            parts.add("분류 " + String.join(NAME_DELIMITER, query.moodCategories()));
        }
        if (query.maxMinutes() != null) {
            parts.add("최대 " + query.maxMinutes() + "분");
        }
        if (!query.keywords().isEmpty()) {
            parts.add("키워드 " + String.join(NAME_DELIMITER, query.keywords()));
        }
        if (query.keywordText() != null) {
            parts.add("요리 이름 " + query.keywordText());
        }
        if (query.preferenceText() != null) {
            parts.add("원한 표현 " + query.preferenceText());
        }
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
        );
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
                + ", 분류=" + query.moodCategories()
                + ", 최대 시간=" + query.maxMinutes()
                + ", 제외 재료=" + query.excludeIngredientIds()
                + ", 선택 재료만=" + query.useSelectedOnly()
                + ", 키워드=" + query.keywords()
                + ", 요리 이름=" + query.keywordText()
                + ", 원한 표현=" + query.preferenceText();
    }
}

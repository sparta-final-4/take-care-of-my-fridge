package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatIntentFacade {

    private final ChatIntentExtractor chatIntentExtractor;
    private final RecipeService recipeService;
    private final IngredientMatcher ingredientMatcher;
    private final ChatCandidateProperties chatCandidateProperties;

    public ChatIntentResolution resolve(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        ChatIntentResolution resolution = decide(memberId, sessionId, request);
        log.info("상담 의도 - memberId: {}, sessionId: {}, 의도: {}, 출처: {}, 조건: {}",
                memberId, sessionId, resolution.intent(), resolution.source(), summarize(resolution.recipeQuery()));
        return resolution;
    }

    private ChatIntentResolution decide(Long memberId, Long sessionId, ChatMessageCreateRequest request) {
        if (request.mode() == ChatMessageMode.RECOMMEND) {
            return ChatIntentResolution.recommend(Source.MODE,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly()));
        }

        IntentResult result;
        try {
            List<String> categories = categories();
            result = chatIntentExtractor.extract(memberId, sessionId, request.content(), categories)
                    .sanitize(categories);
        } catch (RuntimeException e) {
            log.warn("상담 의도 뽑기 실패, 선택 재료 유무로 정함 - memberId: {}, sessionId: {}, 선택 재료 수: {}, 원인: {}",
                    memberId, sessionId, request.selectedIngredientIds().size(), e.toString());
            return request.selectedIngredientIds().isEmpty()
                    ? ChatIntentResolution.general(Source.FALLBACK)
                    : ChatIntentResolution.recommend(Source.FALLBACK,
                    RecipeQuery.of(memberId, request.selectedIngredientIds(), request.useSelectedOnly()));
        }

        if (result.intent() != ChatIntent.RECOMMEND) {
            return ChatIntentResolution.general(Source.AI);
        }
        return ChatIntentResolution.recommend(Source.AI, toRecipeQuery(memberId, sessionId, request, result));
    }

    // 고를 수 있는 레시피 분류: 레시피에 있는 분류(레시피 분류 캐시)에서 후보 제외 분류를 뺀 것
    private List<String> categories() {
        Set<String> excluded = Set.copyOf(chatCandidateProperties.excludedCategories());
        return recipeService.getClassifications().categories().stream()
                .filter(category -> !excluded.contains(category))
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
                useSelectedOnly
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
                + ", 선택 재료만=" + query.useSelectedOnly();
    }
}

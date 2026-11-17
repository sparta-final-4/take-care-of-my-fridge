package org.example.takecareofmyfridgebe.domain.chat.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation.QuerySnapshot;
import org.example.takecareofmyfridgebe.domain.chat.dto.RefineCondition;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 상담 의도 결과(Jev로 정했거나 OpenAI로 되돌린 것)에 서버 서비스 규칙을 적용한다. 아래 순서대로 한 번씩 돈다.
 * <ol>
 *   <li>이어짐은 직전 추천이 있을 때만: 직전 조건(Redis)이 없으면 AI가 이어짐 예라고 해도 아니오로 둔다 (새 추천)</li>
 *   <li>레시피 자세히 대상 찾기: 직전 카드·상담 추천에서 대상을 찾고, 이름만 못 찾으면 그 요리 추천으로 (새 추천)</li>
 *   <li>요리 이름 확인: 추천(이어짐 포함)에서 요리 이름이 메시지에 그대로 없으면 비운다 (단어 목록을 쓰지 않는다)</li>
 *   <li>다른 맛 지키기: 피하는 맛만 말했으면 뺄 조건 TASTE를 지운다</li>
 *   <li>말한 맛만: 새 추천(이어짐 아님)에서 앞 대화에서 따라온 맛을 뺀다</li>
 * </ol>
 * 의도·맛·분류를 단어로 고치는 "AI 실수 고치기" 단계는 Jev 전환 정리에서 지웠다 (docs/worklogs/work-chat-jev-on.md).
 * 결과를 추천 조건(RecipeQuery)으로 바꾸고 다시 추천을 합치는 일은 ChatIntentFacade가 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentCorrector {

    // 레시피 자세히 대상을 이름으로 찾을 상담 전체 추천 수 (최근 추천부터)
    private static final int SESSION_RECIPES_SEARCHED = 30;
    // 줄임말로 볼 메시지 단어의 최소 글자 수
    private static final int MIN_ABBREVIATION_LENGTH = 2;
    // 메시지 단어 끝에서 지울 문장 부호
    private static final String TRAILING_MARKS = "[?!.~…,]+$";

    private final RecipeService recipeService;
    private final IngredientMatcher ingredientMatcher;
    private final ChatMessageService chatMessageService;
    private final ChatWordProperties chatWordProperties;

    /**
     * 규칙을 적용한 결과.
     *
     * @param result       규칙을 적용한 의도·조건
     * @param detailTarget 레시피 자세히 대상 레시피 ID (못 찾았으면 null)
     */
    public record Corrected(IntentResult result, Long detailTarget) {
    }

    /**
     * @param input  규칙 적용 한 번 동안 바뀌지 않는 입력 (회원·상담·메시지·AI에 준 분류 목록·대화 문맥)
     * @param result Jev로 정했거나 OpenAI로 되돌린 의도 결과
     */
    public Corrected correct(CorrectionInput input, IntentResult result) {
        IntentResult corrected = result;
        // 이어짐은 직전 조건을 이어받는 것이라 직전 조건이 없으면(첫 질문, Redis 만료·장애) 새 추천이다
        if (corrected.follows() && input.context().lastRecommendation() == null) {
            log.info("상담 이어짐인데 직전 조건이 없어 새 추천으로 - memberId: {}, sessionId: {}", input.memberId(), input.sessionId());
            corrected = corrected.withFollows(false);
        }
        // 레시피 자세히인데 물어본 레시피가 이 상담 추천에 없으면, 그 요리 이름으로 새 추천을 한다 (아래 규칙을 거치게 여기서 바꾼다)
        Long detailTarget = null;
        if (corrected.intent() == ChatIntent.RECIPE_DETAIL) {
            detailTarget = findTarget(input, corrected);
            if (detailTarget == null && corrected.targetName() != null) {
                log.info("상담 레시피 자세히 대상이 이 상담 추천에 없어 그 요리로 추천 - memberId: {}, sessionId: {}, 이름: {}",
                        input.memberId(), input.sessionId(), corrected.targetName());
                corrected = corrected.withNamed(corrected.includeIngredients(), List.of(corrected.targetName()), corrected.targetName());
            }
        }
        if (corrected.intent() == ChatIntent.RECOMMEND) {
            corrected = keepSpokenDishOnly(input, corrected);
            corrected = keepOtherTastes(input, corrected);
        }
        // 새 추천은 앞 대화의 맛을 잇지 않는다 (앞 조건 이어 가기는 이어짐만). 직전 조건에서 따라온 맛만 이번 말에 없으면 뺀다
        if (corrected.intent() == ChatIntent.RECOMMEND && !corrected.follows()) {
            corrected = keepSpokenTastes(input, corrected);
        }
        return new Corrected(corrected, detailTarget);
    }

    // 요리 이름이 메시지에 그대로 있는지 (공백 무시). 단어 목록을 쓰지 않는다
    static boolean dishInMessage(String message, String dish) {
        return message != null && dish != null && !dish.isBlank()
                && ChatTexts.removeWhitespace(message).contains(ChatTexts.removeWhitespace(dish));
    }

    /**
     * OpenAI 요리 이름을 사용자가 말한 것으로 받을지: 메시지에 그대로 있거나, 메시지의 한 단어(끝 문장 부호를 뺀 두 글자 이상)의 글자가
     * 요리 이름에 순서대로 다 있으면(줄임말을 푼 이름, "된찌" → 된장찌개) 받는다. 정식 이름 확인과 같은 함수(ChatTexts.containsInOrder)를 쓴다.
     * 한 글자 단어("면", "국")는 거의 모든 요리 이름에 들어 있어 보지 않는다. 키워드 줍기(JevIntentResolver.keywordInMessage)는 그대로 글자 그대로만 본다.
     */
    static boolean dishSpoken(String message, String dish) {
        if (dishInMessage(message, dish)) {
            return true;
        }
        if (message == null || dish == null || dish.isBlank()) {
            return false;
        }
        return Arrays.stream(message.strip().split("\\s+"))
                .map(word -> word.replaceAll(TRAILING_MARKS, ""))
                .filter(word -> word.length() >= MIN_ABBREVIATION_LENGTH)
                .anyMatch(word -> ChatTexts.containsInOrder(dish, word));
    }

    // 요리 이름 확인: 메시지에 그대로 없고 줄임말을 푼 이름도 아니면 비운다 (dishSpoken). 분류 옮기기·요리 이름 채우기는 하지 않는다
    IntentResult keepSpokenDishOnly(CorrectionInput input, IntentResult result) {
        if (result.dishText() == null || dishSpoken(input.message(), result.dishText())) {
            return result;
        }
        log.info("상담 요리 이름이 메시지에 없어 비움 - memberId: {}, sessionId: {}, 요리 이름: {}",
                input.memberId(), input.sessionId(), result.dishText());
        return result.withCategories(result.moodCategories(), result.keywords(), null, result.removed());
    }

    // 재료 사전에 있는 이름만 (직접 입력·밀키트 재료로 잡힌 이름은 뺀다). 매칭이 실패하면(DB·검색 장애) 빈 목록
    public List<String> ingredientNames(List<String> words) {
        try {
            List<String> names = new ArrayList<>();
            ingredientMatcher.matchAll(words).forEach((name, ingredient) -> {
                if (ingredient != null && !ingredient.isCustom() && !ingredient.isMealkit()) {
                    names.add(name);
                }
            });
            return names;
        } catch (RuntimeException e) {
            log.warn("상담 재료 이름 매칭 실패, 재료 없이 - 원인: {}", e.toString());
            return List.of();
        }
    }

    /**
     * 피하는 맛만 말했으면("매운 건 빼줘") AI가 넣은 뺄 조건 TASTE를 지운다. AI가 "빼줘"를 맛 조건 지우기로 읽으면
     * 다시 추천에서 직전에 원한 다른 맛("짭짤한 거")까지 사라지기 때문이다. 피할 맛은 그대로 둔다.
     * 맛 조건을 지우라는 말("맛 상관없이", "맛은 아무거나")이 있으면 AI 값을 그대로 쓴다. 지웠으면 INFO로 남긴다.
     */
    IntentResult keepOtherTastes(CorrectionInput input, IntentResult result) {
        List<String> removed = orEmpty(result.removed());
        if (!removed.contains(RefineCondition.TASTE.name())
                || TasteWordChecker.clearsTastes(input.message(), chatWordProperties.taste())) {
            return result;
        }
        boolean avoids = TasteWordChecker.stances(input.message(), chatWordProperties.taste()).stream()
                .anyMatch(stance -> stance.stance() == TasteWordChecker.Stance.AVOID);
        if (!avoids) {
            return result;
        }
        List<String> kept = removed.stream().filter(condition -> !condition.equals(RefineCondition.TASTE.name())).toList();
        log.info("상담 피하는 맛만 말해서 맛 조건 지우기를 뺌 - memberId: {}, sessionId: {}, removed: {} → {}",
                input.memberId(), input.sessionId(), removed, kept);
        return result.withRemoved(kept);
    }

    /**
     * 새 추천에서 앞 대화에서 따라온 맛을 뺀다: 직전 조건(Redis 문맥)의 원하는 맛·피할 맛에 있던 맛이고, 이번 말에 그 맛 단어가 없을 때만
     * (TasteWordChecker.mentioned). "매운 거 말고" 다음 "김치찌개"에서 AI가 앞의 피할 맛(매운맛)을 따라 넣어 김치찌개가 빠지는 것을 막는다.
     * 직전 조건이 없거나 직전에 없던 맛은 그대로 둔다. 맛 단어 목록에 없는 말("맵고", "화끈한", "얼얼한")에서 맞게 넣은 맛을
     * 지우지 않기 위해서다. 비웠으면 INFO로 남긴다.
     */
    IntentResult keepSpokenTastes(CorrectionInput input, IntentResult result) {
        QuerySnapshot previous = input.context().lastRecommendation() == null
                ? null : input.context().lastRecommendation().query();
        if (previous == null) {
            return result;
        }
        Set<Member.Taste> carried = new HashSet<>(previous.wantedTastes());
        if (previous.avoidTastes() != null) {
            carried.addAll(previous.avoidTastes());
        }
        Set<Member.Taste> spoken = TasteWordChecker.mentioned(input.message(), chatWordProperties.taste());
        List<String> wanted = orEmpty(result.wantedTastes()).stream().filter(taste -> !carriedOnly(carried, spoken, taste)).toList();
        List<String> avoid = orEmpty(result.avoidTastes()).stream().filter(taste -> !carriedOnly(carried, spoken, taste)).toList();
        if (wanted.size() == orEmpty(result.wantedTastes()).size() && avoid.size() == orEmpty(result.avoidTastes()).size()) {
            return result;
        }
        log.info("상담 새 추천이라 앞 대화에서 따라온 맛을 뺌 - memberId: {}, sessionId: {}, 원하는 맛: {} → {}, 피할 맛: {} → {}",
                input.memberId(), input.sessionId(), result.wantedTastes(), wanted, result.avoidTastes(), avoid);
        return result.withTastes(wanted, avoid);
    }

    // 맛 이름(SPICY 등)이 직전 조건에 있었고 이번 말에는 나오지 않은 맛인지 (앞 대화에서 따라온 맛). 모르는 이름은 sanitize가 이미 걸렀다
    private static boolean carriedOnly(Set<Member.Taste> carried, Set<Member.Taste> spoken, String taste) {
        try {
            Member.Taste value = Member.Taste.valueOf(taste);
            return carried.contains(value) && !spoken.contains(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 물어본 레시피 ID. 이름이 직전 카드에 있으면 그 카드 → 직전 카드 번호 → 상담 전체 추천(최근부터)에서 이름 순으로 찾는다.
     * 상담 전체 추천은 앞에서 못 찾았을 때만 읽는다 (추천 1번, 이름 1번).
     */
    Long findTarget(CorrectionInput input, IntentResult result) {
        List<CardName> lastCards = input.context().lastCards();
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
                .findRecentRecommendedRecipeIds(List.of(input.sessionId()), SESSION_RECIPES_SEARCHED)
                .getOrDefault(input.sessionId(), List.of());
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
        String target = ChatTexts.removeWhitespace(name);
        return cards.stream()
                .filter(card -> {
                    String cardName = ChatTexts.removeWhitespace(card.name());
                    return cardName.contains(target) || target.contains(cardName);
                })
                .map(CardName::recipeId)
                .findFirst();
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}

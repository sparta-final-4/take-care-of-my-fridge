package org.example.takecareofmyfridgebe.domain.chat.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI가 뽑은 의도·조건(sanitize 뒤)을 서버 단어 규칙으로 한 번 더 고친다. 아래 순서대로 한 번씩 돌고, 앞 단계 결과를 뒤 단계가 쓴다.
 * <ol>
 *   <li>상관없는 질문 되살리기 (offTopicRevive): 음식 말이 있으면 OFF_TOPIC → GENERAL·RECOMMEND</li>
 *   <li>레시피 자세히 대상 찾기: 직전 카드·상담 추천에서 대상을 찾고, 이름만 못 찾으면 그 요리 추천으로</li>
 *   <li>냉장고 보기·사기 말 (fridgeShopWords): 의도를 FRIDGE·SHOP·RECOMMEND·GENERAL로. 4·5보다 먼저</li>
 *   <li>밀키트 요청 (mealkitRequest): 일반 답이지만 밀키트를 달라는 말이면 추천으로 표시 (의도는 GENERAL 그대로)</li>
 *   <li>이름만 말하기 (nameOnly): 일반 답인 짧은 이름 → 추천 (4가 아닐 때)</li>
 *   <li>요리 이름·분류 키워드 확인 (checkDishAndKeywords, 스위치 dishCheck): AI 요리 이름은 메시지에 그대로 있고 분류 이름·묶음 말이
 *       아닐 때만 두고, 분류 이름·묶음 말인 키워드는 분류로 옮김 (추천·다시 추천·밀키트 추천)</li>
 *   <li>다른 맛 지키기 (keepOtherTastes): 피하는 맛만 말했으면 뺄 조건 TASTE를 지움</li>
 *   <li>맛 단어 (tasteWords): 원하는 맛·피할 맛</li>
 *   <li>분류 단어 (categoryWords): 분류, 앞 대화에서 이어진 키워드·요리 이름</li>
 *   <li>말한 맛만 (spokenTastesOnly): 새 추천에서 앞 대화에서 따라온 맛을 뺌</li>
 * </ol>
 * 요리 이름·맛·분류 보정(6~9)은 추천·다시 추천·밀키트 추천일 때만 한다. 일반 답·레시피 자세히는 AI 결과를 그대로 쓴다.
 * 결과를 추천 조건(RecipeQuery)으로 바꾸고 다시 추천을 합치는 일은 ChatIntentFacade가 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentCorrector {

    // 레시피 자세히 대상을 이름으로 찾을 상담 전체 추천 수 (최근 추천부터)
    private static final int SESSION_RECIPES_SEARCHED = 30;
    // 이름만 말한 말로 볼 최대 글자 수 (공백·끝 문장 부호 뺀 글). "닭가슴살 브로콜리 만두"(11자)·"매운 국물 밀키트"(7자)가 들어간다
    static final int NAME_ONLY_MAX_LENGTH = 15;
    // 분류 이름 뒤에 붙어도 분류 이름으로 보는 말 ("디저트 메뉴", "국물 요리")
    private static final List<String> CATEGORY_SUFFIXES = List.of("요리", "메뉴", "종류");

    private final RecipeService recipeService;
    private final IngredientMatcher ingredientMatcher;
    private final ChatMessageService chatMessageService;
    private final ChatWordProperties chatWordProperties;
    // 보정마다 켜고 끄기 (기본 모두 켜짐). 평가로 AI만으로 충분한지 비교할 때 끈다
    private final ChatCorrectionProperties corrections;

    /**
     * 고친 결과.
     *
     * @param result           고친 의도·조건
     * @param mealkitRecommend 일반 답이지만 밀키트를 달라는 말이라 추천으로 처리할지
     * @param detailTarget     레시피 자세히 대상 레시피 ID (못 찾았으면 null)
     */
    public record Corrected(IntentResult result, boolean mealkitRecommend, Long detailTarget) {
    }

    /**
     * @param input     보정 한 번 동안 바뀌지 않는 입력 (회원·상담·메시지·AI에 준 분류 목록·대화 문맥)
     * @param sanitized AI 결과를 sanitize한 것
     */
    public Corrected correct(CorrectionInput input, IntentResult sanitized) {
        IntentResult result = sanitized;
        // AI가 상관없는 질문이라고 해도 음식 말이 있으면 거절하지 않는다 (정상 질문을 거절하는 게 상관없는 질문을 통과시키는 것보다 나쁘다).
        // 되살린 GENERAL·RECOMMEND는 아래에서 AI가 처음부터 그렇게 본 것처럼 처리한다. BLOCKED·SELF_HARM은 되살리지 않는다
        if (result.intent() == ChatIntent.OFF_TOPIC && corrections.offTopicRevive()) {
            result = reviveOffTopic(input, result);
        }
        // 레시피 자세히인데 물어본 레시피가 이 상담 추천에 없으면, 그 요리 이름으로 새 추천을 한다 (아래 맛·분류 보정을 거치게 여기서 바꾼다)
        Long detailTarget = null;
        if (result.intent() == ChatIntent.RECIPE_DETAIL) {
            detailTarget = findTarget(input, result);
            if (detailTarget == null && result.targetName() != null) {
                log.info("상담 레시피 자세히 대상이 이 상담 추천에 없어 그 요리로 추천 - memberId: {}, sessionId: {}, 이름: {}",
                        input.memberId(), input.sessionId(), result.targetName());
                result = result.withNamed(result.includeIngredients(), List.of(result.targetName()), result.targetName());
            }
        }
        // 냉장고 보기 말이면 AI가 추천·일반 대화로 봐도(되살린 것 포함) 냉장고 보기로 바꾼다. 아래 밀키트·이름만 말하기 규칙보다 먼저 본다
        if (corrections.fridgeShopWords()) {
            result = applyFridgeShopWords(input, result);
        }
        boolean mealkitRecommend = corrections.mealkitRequest() && result.intent() == ChatIntent.GENERAL
                && mealkitRequested(input);
        // "국물 밀키트", "김치찌개", "두부"처럼 이름만 말한 짧은 말은 보여 달라는 뜻으로 보고 추천으로 바꾼다
        if (corrections.nameOnly() && result.intent() == ChatIntent.GENERAL && !mealkitRecommend) {
            Optional<IntentResult> named = nameOnly(input, result);
            if (named.isPresent()) {
                result = named.get();
            }
        }
        // 맛·분류 단어로 조건을 고치는 것은 추천·다시 추천일 때만 한다. 일반 답·레시피 자세히는 AI 결과를 그대로 쓴다
        if (result.intent() == ChatIntent.RECOMMEND || result.intent() == ChatIntent.REFINE || mealkitRecommend) {
            // AI 요리 이름은 메시지에 있는지만 확인하고, 분류 이름·묶음 말("디저트", "국물")은 키워드·요리 이름이 아니라 분류다
            result = checkDishAndKeywords(input, result);
            if (corrections.keepOtherTastes()) {
                result = keepOtherTastes(input, result);
            }
            if (corrections.tasteWords()) {
                result = correctTastes(input, result);
            }
            if (corrections.categoryWords()) {
                result = correctCategories(input, result);
            }
        }
        // 새 추천은 앞 대화의 맛을 잇지 않는다 (앞 조건 이어 가기는 다시 추천만). 직전 조건에서 따라온 맛만 이번 말에 없으면 뺀다
        if ((result.intent() == ChatIntent.RECOMMEND || mealkitRecommend) && corrections.spokenTastesOnly()) {
            result = keepSpokenTastes(input, result);
        }
        return new Corrected(result, mealkitRecommend, detailTarget);
    }

    /**
     * AI가 뽑은 요리 이름을 확인하고, 분류 이름·묶음 말인 키워드는 키워드에서 빼고 분류로 옮긴다.
     * <ul>
     *   <li>요리 이름(dishText)은 메시지에 그대로 있고(공백 무시) 분류 이름·묶음 말이 아닐 때만 둔다. 아니면 비운다
     *       ("김피탕 같은 요리 추천해줘" → 김피탕 그대로, AI가 "디저트"·메시지에 없는 "냉채"를 내면 없음).
     *       스위치 dishCheck를 끄면 AI 값을 그대로 쓴다 (평가 비교용)</li>
     *   <li>"차가운 요리"의 키워드 [샐러드, 냉채, 냉국] → 키워드 [냉채, 냉국], 분류에 샐러드를 더함 (이미 있으면 그대로)</li>
     *   <li>묶음 말("국물", "반찬")은 묶음의 분류(분류 목록에 있는 것)로 옮긴다</li>
     *   <li>이번 말에서 피하는 분류("디저트 말고")는 옮기지 않고 빼기만 한다 (CategoryWordChecker)</li>
     * </ul>
     * 분류는 분류 조건으로만 쓴다. 키워드에 남으면 요리 이름 후보가 되어 "디저트 레시피는 없어서…" 안내가 붙는다.
     * "이 말이 분류·묶음 말인지"는 {@link #categoriesOf}에서만 본다. 바꿨으면 INFO로 남긴다.
     */
    IntentResult checkDishAndKeywords(CorrectionInput input, IntentResult result) {
        List<String> keywords = orEmpty(result.keywords());
        List<String> aiCategories = orEmpty(result.moodCategories());
        List<String> kept = new ArrayList<>();
        List<String> moved = new ArrayList<>(aiCategories);
        Set<String> avoided = CategoryWordChecker.check(input.message(), input.categories(), chatWordProperties.taste(),
                        chatWordProperties.category().groups())
                .stream()
                .filter(stance -> stance.stance() == CategoryWordChecker.Stance.AVOID)
                .map(CategoryWordChecker.CategoryStance::category)
                .collect(Collectors.toSet());
        for (String keyword : keywords) {
            Optional<List<String>> mapped = categoriesOf(keyword, input.categories());
            if (mapped.isEmpty()) {
                kept.add(keyword);
                continue;
            }
            mapped.get().stream().filter(category -> !avoided.contains(category)).forEach(category -> addOnce(moved, category));
        }
        String dishText = result.dishText();
        if (corrections.dishCheck() && dishText != null
                && (!ChatTexts.removeWhitespace(input.message()).contains(ChatTexts.removeWhitespace(dishText))
                || categoriesOf(dishText, input.categories()).isPresent())) {
            dishText = null;
        }
        if (kept.size() == keywords.size() && moved.size() == aiCategories.size() && Objects.equals(dishText, result.dishText())) {
            return result;
        }
        log.info("상담 요리 이름 확인·분류 키워드 옮김 - memberId: {}, sessionId: {}, 요리 이름: {} → {}, 키워드: {} → {}, 분류: {} → {}",
                input.memberId(), input.sessionId(), result.dishText(), dishText, keywords, kept, aiCategories, moved);
        return result.withCategories(result.moodCategories() == null && moved.isEmpty() ? null : moved,
                result.keywords() == null ? null : kept, dishText, result.removed());
    }

    /**
     * 분류 이름(categories)이나 묶음 말(app.chat.words.category: 국물·반찬·간식…)이면 그 말이 뜻하는 분류(분류 목록에 있는 것만),
     * 아니면 비어 있음. 공백을 빼고 비교하고, 뒤에 붙은 "요리·메뉴·종류"는 떼고 본다 ("디저트 메뉴", "국물 요리").
     * "김치찌개"·"국수"·"매운 국"처럼 다른 말이 붙으면 분류가 아니다.
     */
    Optional<List<String>> categoriesOf(String phrase, List<String> categories) {
        if (phrase == null) {
            return Optional.empty();
        }
        String compact = ChatTexts.removeWhitespace(phrase);
        for (String suffix : CATEGORY_SUFFIXES) {
            if (compact.length() > suffix.length() && compact.endsWith(suffix)) {
                compact = compact.substring(0, compact.length() - suffix.length());
                break;
            }
        }
        String name = compact;
        Optional<String> category = categories.stream().filter(value -> ChatTexts.removeWhitespace(value).equals(name)).findFirst();
        if (category.isPresent()) {
            return Optional.of(List.of(category.get()));
        }
        return chatWordProperties.category().groups().stream()
                .filter(group -> group.word() != null && ChatTexts.removeWhitespace(group.word()).equals(name))
                .findFirst()
                .map(group -> group.categories().stream().filter(categories::contains).toList());
    }

    /**
     * AI가 상관없는 질문(OFF_TOPIC)이라고 본 말에 음식 말이 있으면 되살린다. 달라는 말이 있으면 RECOMMEND, 없으면 GENERAL.
     * <ul>
     *   <li>음식 말(app.chat.words.food, 공백 빼고) → 분류 단어(묶음 말 포함) → 재료 사전 이름 → 레시피 이름 순으로 보고, 앞에서 찾으면 뒤는 보지 않는다</li>
     *   <li>예) "주식으로 먹을 거 추천해줘" → RECOMMEND, "장보기 목록 짜줘" → GENERAL, "주식 추천해줘"·"파이썬 코드 짜줘" → OFF_TOPIC 그대로</li>
     *   <li>재료·레시피 이름 찾기가 실패하면(DB·검색 장애) 찾지 못한 것으로 본다</li>
     * </ul>
     */
    IntentResult reviveOffTopic(CorrectionInput input, IntentResult result) {
        Optional<String> found = foodMention(input.message(), input.categories());
        if (found.isEmpty()) {
            return result;
        }
        ChatIntent intent = AskWordChecker.requests(input.message(), chatWordProperties.ask())
                ? ChatIntent.RECOMMEND : ChatIntent.GENERAL;
        log.info("상담 음식 말이 있어 상관없는 질문을 되살림 - memberId: {}, sessionId: {}, 찾은 것: {}, 의도: {}",
                input.memberId(), input.sessionId(), found.get(), intent);
        return result.withIntent(intent);
    }

    /**
     * 냉장고 보기·사기 말(app.chat.words.fridge-shop)로 의도를 한 번 더 정한다 (FridgeShopWordChecker).
     * <ul>
     *   <li>AI가 추천·일반 대화로 본 말에 냉장고 보기 말이 있으면 FRIDGE ("밀키트 뭐 남았어", "두부 아직 있어?")</li>
     *   <li>냉장고 보기 말이 없고 사기 말이 있으면 SHOP ("당근 사고 싶어", "대파 장바구니에 담아줘")</li>
     *   <li>묻는 말("어떻게 해동해?", "먹어도 돼?")이나 요리 말("뭐 먹지", "요리 추천")이 있으면 바꾸지 않는다</li>
     *   <li>AI가 냉장고 보기·사기로 봤어도 요리 말이 있으면 추천으로 둔다 ("두부 있으면 두부 요리 추천해줘")</li>
     *   <li>AI가 냉장고 보기·사기로 봤어도 묻는 말(fridge-shop.general)이 있으면 일반 답으로 ("유통기한 지난 거 먹어도 돼?", "주문한 거 언제 와?")</li>
     * </ul>
     * 다시 추천·레시피 자세히·거절 의도는 건드리지 않는다. 바꿨으면 INFO로 남긴다.
     */
    IntentResult applyFridgeShopWords(CorrectionInput input, IntentResult result) {
        ChatIntent intent = result.intent();
        if ((intent == ChatIntent.FRIDGE || intent == ChatIntent.SHOP)
                && FridgeShopWordChecker.cooks(input.message(), chatWordProperties.fridgeShop())) {
            log.info("상담 요리 말이 있어 {}를 추천으로 - memberId: {}, sessionId: {}", intent, input.memberId(), input.sessionId());
            return result.withIntent(ChatIntent.RECOMMEND);
        }
        if ((intent == ChatIntent.FRIDGE || intent == ChatIntent.SHOP)
                && FridgeShopWordChecker.asksGeneral(input.message(), chatWordProperties.fridgeShop())) {
            log.info("상담 묻는 말이 있어 {}를 일반 답으로 - memberId: {}, sessionId: {}", intent, input.memberId(), input.sessionId());
            return result.withIntent(ChatIntent.GENERAL);
        }
        if (intent != ChatIntent.RECOMMEND && intent != ChatIntent.GENERAL) {
            return result;
        }
        Optional<ChatIntent> changed = FridgeShopWordChecker.check(input.message(), chatWordProperties.fridgeShop(),
                chatWordProperties.ask());
        if (changed.isEmpty()) {
            return result;
        }
        log.info("상담 냉장고 보기 말이 있어 의도를 바꿈 - memberId: {}, sessionId: {}, 의도: {} → {}",
                input.memberId(), input.sessionId(), intent, changed.get());
        return result.withIntent(changed.get());
    }

    // 보낸 말에 있는 음식 말의 종류 (음식 말·분류·재료·레시피 이름). 로그에는 종류만 남긴다
    private Optional<String> foodMention(String message, List<String> categories) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String compact = message.replaceAll("\\s+", "");
        if (chatWordProperties.food().food().stream().map(word -> word.replaceAll("\\s+", ""))
                .anyMatch(word -> !word.isEmpty() && compact.contains(word))) {
            return Optional.of("음식 말");
        }
        if (!CategoryWordChecker.check(message, categories, chatWordProperties.taste(), chatWordProperties.category().groups()).isEmpty()) {
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
    private boolean mealkitRequested(CorrectionInput input) {
        boolean mealkit = MealkitWordChecker.check(input.message(), chatWordProperties.mealkit(), chatWordProperties.taste())
                == MealkitWordChecker.Mode.ONLY;
        if (!mealkit) {
            return false;
        }
        if (AskWordChecker.requestsMealkit(input.message(), chatWordProperties.ask())) {
            log.info("상담 밀키트를 달라고 해 일반 대화를 추천으로 - memberId: {}, sessionId: {}", input.memberId(), input.sessionId());
            return true;
        }
        if (AskWordChecker.asks(input.message(), chatWordProperties.ask())) {
            log.info("상담 밀키트를 말했지만 묻는 말이라 일반 대화 그대로 - memberId: {}, sessionId: {}", input.memberId(), input.sessionId());
        }
        return false;
    }

    /**
     * 이름만 말한 짧은 말("국물 밀키트", "디저트", "두부", "김치찌개")은 그걸 보여 달라는 뜻이라 AI가 GENERAL이어도 추천으로 바꾼다.
     * <ul>
     *   <li>묻는 말이 없고(AskWordChecker), 공백·끝 문장 부호를 뺀 글이 {@value #NAME_ONLY_MAX_LENGTH}자 이하일 때만 본다</li>
     *   <li>밀키트 단어, 분류 단어(묶음 말 포함), 재료 사전 이름, 레시피 이름 중 하나가 있으면 바꾼다 (앞에서 찾으면 뒤는 보지 않는다).
     *       재료 이름이면 그 재료를 말한 재료로, 레시피 이름이면 그 이름을 키워드와 요리 이름으로 채운다.
     *       재료·레시피 이름은 요리를 달라는 말("레시피", "만드는 법")을 지우고 찾고, 레시피 이름은 맛 단어 낱말을 뺀 이름으로도 찾는다
     *       ("매운 닭볶음탕 레시피" → 닭볶음탕). 분류·맛은 추천 조건 보정(correctCategories·correctTastes)이 채운다</li>
     *   <li>인사 말("안녕 밥은?")이 있거나 지난 일을 묻는 말("밥 먹었어?")이면 바꾸지 않는다</li>
     *   <li>"안녕"처럼 음식 말이 없으면 그대로 일반 답이다</li>
     * </ul>
     */
    Optional<IntentResult> nameOnly(CorrectionInput input, IntentResult result) {
        if (input.message() == null || AskWordChecker.asks(input.message(), chatWordProperties.ask())
                || AskWordChecker.greets(input.message(), chatWordProperties.ask())) {
            return Optional.empty();
        }
        String text = input.message().strip().replaceAll("[?!.~…,]+$", "").strip();
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty() || compact.length() > NAME_ONLY_MAX_LENGTH) {
            return Optional.empty();
        }
        String found = null;
        List<String> includes = List.of();
        List<String> keywords = List.of();
        if (MealkitWordChecker.check(input.message(), chatWordProperties.mealkit(), chatWordProperties.taste())
                == MealkitWordChecker.Mode.ONLY) {
            found = "밀키트";
        } else if (CategoryWordChecker.check(input.message(), input.categories(), chatWordProperties.taste(),
                        chatWordProperties.category().groups())
                .stream().anyMatch(stance -> stance.stance() == CategoryWordChecker.Stance.WANT)) {
            found = "분류";
        } else {
            // "닭볶음탕 레시피"의 "레시피"처럼 요리를 달라는 말은 지우고 찾는다
            String name = withoutRecipeWords(text);
            List<String> ingredients = name.isEmpty() ? List.of() : ingredientNames(List.of(name.split("\\s+")));
            Optional<String> recipeName = ingredients.isEmpty() ? recipeNameIn(name) : Optional.empty();
            if (!ingredients.isEmpty()) {
                found = "재료";
                includes = ingredients;
            } else if (recipeName.isPresent()) {
                found = "레시피 이름";
                keywords = List.of(recipeName.get());
            }
        }
        if (found == null) {
            return Optional.empty();
        }
        log.info("상담 이름만 말해 일반 대화를 추천으로 - memberId: {}, sessionId: {}, 찾은 것: {}, 재료: {}, 키워드: {}",
                input.memberId(), input.sessionId(), found, includes, keywords);
        // 레시피 이름을 찾았으면 그 이름이 요리 이름이다 (AI는 일반 답으로 봐서 요리 이름을 내지 않았다)
        return Optional.of(result.withNamed(includes.isEmpty() ? result.includeIngredients() : includes,
                keywords.isEmpty() ? result.keywords() : keywords, keywords.isEmpty() ? result.dishText() : keywords.get(0)));
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
    IntentResult correctTastes(CorrectionInput input, IntentResult result) {
        List<String> wanted = new ArrayList<>(result.wantedTastes() == null ? List.of() : result.wantedTastes());
        List<String> avoid = new ArrayList<>(result.avoidTastes() == null ? List.of() : result.avoidTastes());
        for (TasteWordChecker.TasteStance stance : TasteWordChecker.stances(input.message(), chatWordProperties.taste())) {
            String taste = stance.taste().name();
            boolean want = stance.stance() == TasteWordChecker.Stance.WANT;
            if (want && avoid.contains(taste) && !wanted.contains(taste)
                    && !TasteWordChecker.surelyWanted(input.message(), stance.taste(), chatWordProperties.taste())) {
                log.debug("상담 맛 단어가 애매해 AI 피할 맛을 그대로 둠 - memberId: {}, sessionId: {}, 맛: {}",
                        input.memberId(), input.sessionId(), taste);
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
                input.memberId(), input.sessionId(), result.wantedTastes(), result.avoidTastes(), wanted, avoid);
        return result.withTastes(wanted, avoid);
    }

    // 요리를 달라는 말(ask.recipe: 레시피·만드는법…)을 띄어쓰기와 상관없이 지운 글 ("닭볶음탕 만드는 법" → "닭볶음탕")
    String withoutRecipeWords(String text) {
        String result = text;
        for (String word : chatWordProperties.ask().recipe()) {
            String compact = word.replaceAll("\\s+", "");
            if (compact.isEmpty()) {
                continue;
            }
            String spaced = compact.chars()
                    .mapToObj(ch -> Pattern.quote(String.valueOf((char) ch)))
                    .collect(Collectors.joining("\\s*"));
            result = result.replaceAll(spaced, " ");
        }
        return result.replaceAll("\\s+", " ").strip();
    }

    /**
     * 레시피 이름을 찾는다: 이름 그대로 → 맛 단어로 시작하는 낱말을 뺀 이름 순 (앞에서 찾으면 뒤는 보지 않는다).
     * "매운탕"은 그대로 찾고, "매운 닭볶음탕"은 "매운"을 빼고 "닭볶음탕"으로 찾는다 (레시피 검색은 모든 낱말이 이름에 있어야 한다).
     */
    private Optional<String> recipeNameIn(String name) {
        for (String candidate : new LinkedHashSet<>(List.of(name, withoutTasteWords(name)))) {
            if (!candidate.isEmpty() && !recipeService.suggestNames(candidate, 1).isEmpty()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // 맛 단어(매운·달달·순한…)로 시작하는 낱말을 뺀 글
    private String withoutTasteWords(String text) {
        List<String> tasteWords = Stream.of(chatWordProperties.taste().spicy(), chatWordProperties.taste().sweet(),
                        chatWordProperties.taste().salty(), chatWordProperties.taste().umami(), chatWordProperties.taste().mild())
                .flatMap(List::stream)
                .toList();
        return Stream.of(text.split("\\s+"))
                .filter(token -> tasteWords.stream().noneMatch(token::startsWith))
                .collect(Collectors.joining(" "))
                .strip();
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
     * 직전 조건이 없거나 직전에 없던 맛은 AI 값 그대로 둔다. 맛 단어 목록에 없는 말("맵고", "화끈한", "얼얼한")에서 AI가 맞게 넣은 맛을
     * 지우지 않기 위해서다 (평가 T11~T14). 비웠으면 INFO로 남긴다.
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
    IntentResult correctCategories(CorrectionInput input, IntentResult result) {
        List<CategoryWordChecker.CategoryStance> stances = CategoryWordChecker.check(input.message(), input.categories(),
                chatWordProperties.taste(),
                chatWordProperties.category().groups());
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
            QuerySnapshot previous = input.context().lastRecommendation() == null
                ? null : input.context().lastRecommendation().query();
            List<String> previousKeywords = previous == null ? List.of() : previous.keywords();
            String compactMessage = ChatTexts.removeWhitespace(input.message());
            // 직전 조건에서 끌려온 키워드(이번 말에 없는 것)만 뺀다. 이번 말로 AI가 늘린 키워드는 둔다
            keywords = keywords == null ? null : keywords.stream()
                    .filter(keyword -> !previousKeywords.contains(keyword) || compactMessage.contains(ChatTexts.removeWhitespace(keyword)))
                    .toList();
            dishText = dishText != null && compactMessage.contains(ChatTexts.removeWhitespace(dishText)) ? dishText : null;
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
                input.memberId(), input.sessionId(), change, aiCategories, finalCategories, wanted, avoided, result.keywords(),
                keywords, removed);
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

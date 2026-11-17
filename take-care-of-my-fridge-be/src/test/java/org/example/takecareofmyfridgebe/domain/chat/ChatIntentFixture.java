package org.example.takecareofmyfridgebe.domain.chat;

import org.example.takecareofmyfridgebe.domain.chat.ai.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.SimilarDishFinder;
import org.example.takecareofmyfridgebe.domain.chat.intent.AskWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.intent.FridgeShopWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.intent.IntentCorrector;
import org.example.takecareofmyfridgebe.domain.chat.intent.JevIntentResolver;
import org.example.takecareofmyfridgebe.domain.chat.intent.MealkitWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.intent.TasteWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.recommend.TemperatureWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyExclusionFinder;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 상담 의도 판단 테스트(ChatIntentFacadeTest, ChatIntentFacadeJevOnTest, IntentCorrectorTest)가 같이 쓰는 준비 코드.
 * 목과 기본 응답(알레르기 없음, 레시피 분류 목록)을 만들고, 진짜 IntentCorrector·JevIntentResolver를 넣은 ChatIntentFacade를 만든다.
 * Jev 호출(JevIntentRunner)과 넓은 요청 요리 이름(SimilarDishFinder)은 흉내 낸다. Jev 판단을 주지 않으면 Jev 실패다.
 * 테스트마다 새로 만든다.
 */
public class ChatIntentFixture {

    public static final Long MEMBER_ID = 1L;
    public static final Long SESSION_ID = 10L;
    public static final Long TOFU = 21L;
    public static final Long MUSHROOM = 161L;
    public static final Long CUCUMBER = 15L;
    // givenAi가 Jev에 주는 의도 confidence·1등 분류 확률
    public static final double JEV_CONFIDENCE = 0.95;

    // config/chat-words.yml app.chat.words.taste-opposites와 같은 쌍
    public static final ChatWordProperties.TasteOpposites TASTE_OPPOSITES = new ChatWordProperties.TasteOpposites(List.of(
            List.of(Taste.MILD, Taste.SPICY), List.of(Taste.MILD, Taste.SALTY)));

    // 단어 설정 전체. 묶음별 고정값은 각 WordCheckerTest가 가진다
    public static final ChatWordProperties WORDS = new ChatWordProperties(TasteWordCheckerTest.WORDS, TASTE_OPPOSITES,
            AskWordCheckerTest.WORDS, MealkitWordCheckerTest.WORDS, FridgeShopWordCheckerTest.WORDS, TemperatureWordCheckerTest.WORDS);

    public final ChatIntentExtractor extractor = mock(ChatIntentExtractor.class);
    public final RecipeService recipeService = mock(RecipeService.class);
    public final IngredientMatcher ingredientMatcher = mock(IngredientMatcher.class);
    public final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    public final AllergyExclusionFinder allergyExclusionFinder = mock(AllergyExclusionFinder.class);
    public final JevIntentRunner jev = mock(JevIntentRunner.class);
    public final JevIntentRunner.Run run = mock(JevIntentRunner.Run.class);
    public final SimilarDishFinder similarDishFinder = mock(SimilarDishFinder.class);
    public final ChatIntentFacade facade;

    public ChatIntentFixture() {
        when(allergyExclusionFinder.find(any(), any())).thenReturn(AllergyExclusionFinder.Allergens.NONE);
        when(jev.start(any(), any(), any())).thenReturn(run);
        when(jev.await(any())).thenReturn(Optional.empty());
        facade = newFacade(newCorrector());
        when(recipeService.getClassifications())
                .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "기타", "샐러드", "양념", "음료", "찌개")));
    }

    // 이 fixture의 Jev 호출로 정하는 facade
    public ChatIntentFacade newFacade(IntentCorrector corrector) {
        return newFacade(corrector, jev);
    }

    // 주어진 Jev 호출로 정하는 facade
    public ChatIntentFacade newFacade(JevIntentRunner jevIntentRunner) {
        return newFacade(newCorrector(), jevIntentRunner);
    }

    private ChatIntentFacade newFacade(IntentCorrector corrector, JevIntentRunner jevIntentRunner) {
        return new ChatIntentFacade(extractor, recipeService, ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                WORDS, allergyExclusionFinder, corrector, jevIntentRunner, new JevIntentResolver(similarDishFinder, WORDS));
    }

    public IntentCorrector newCorrector() {
        return new IntentCorrector(recipeService, ingredientMatcher, chatMessageService, WORDS);
    }

    // OpenAI가 꺼낸 값
    public void givenExtracted(IntentExtraction extraction) {
        when(extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any())).thenReturn(extraction);
    }

    public void givenJev(JevIntentJudgement judgement) {
        when(jev.await(run)).thenReturn(Optional.of(judgement));
    }

    /**
     * Jev와 OpenAI가 함께 이 결과를 내게 한다.
     * Jev: 의도(confidence {@value #JEV_CONFIDENCE}), 이어짐(followsPrevious가 true면 0.9), 원하는·피할 맛,
     * 요청 종류(요리 이름이 있으면 특정 요리, 없고 분류가 있으면 특정 분류, 둘 다 없으면 없음)와 1등 분류(첫 분류).
     * OpenAI: 나머지 꺼낸 값 (키워드·요리 이름·재료·시간 등). 분류는 Jev 1등 하나만 쓰므로 둘째 분류부터는 버려진다.
     */
    public void givenAi(IntentResult result) {
        givenExtracted(extractionOf(result));
        givenJev(judgementOf(result));
    }

    public static IntentExtraction extractionOf(IntentResult result) {
        return new IntentExtraction(result.maxMinutes(), result.includeIngredients(), result.excludeIngredients(),
                result.useSelectedOnly(), result.keywords(), result.dishText(), result.preferenceText(), result.targetIndex(),
                result.targetName(), result.removed(), result.detailQuestion(), result.fridgeScope());
    }

    public static JevIntentJudgement judgementOf(IntentResult result) {
        ChatIntent intent = result.intent();
        Double followsPrevious = Boolean.TRUE.equals(result.followsPrevious()) ? 0.9 : null;
        if (intent != ChatIntent.RECOMMEND) {
            return new JevIntentJudgement(first(intent, JEV_CONFIDENCE, followsPrevious), null, 200L);
        }
        Map<Taste, TasteStance> stances = new EnumMap<>(Taste.class);
        tasteNames(result.wantedTastes()).forEach(taste -> stances.put(taste, TasteStance.WANTED));
        tasteNames(result.avoidTastes()).forEach(taste -> stances.put(taste, TasteStance.AVOIDED));
        List<String> categories = result.moodCategories() == null ? List.of() : result.moodCategories();
        String category = categories.isEmpty() ? null : categories.get(0);
        boolean dish = result.dishText() != null && !result.dishText().isBlank();
        RequestKind kind = dish ? RequestKind.DISH : category != null ? RequestKind.CATEGORY : RequestKind.NONE;
        return new JevIntentJudgement(first(intent, JEV_CONFIDENCE, followsPrevious),
                second(kind, category, JEV_CONFIDENCE, stances), 300L);
    }

    private static List<Taste> tasteNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        return names.stream()
                .filter(name -> Arrays.stream(Taste.values()).anyMatch(taste -> taste.name().equals(name)))
                .map(Taste::valueOf)
                .toList();
    }

    // 1차: 의도와 confidence (다른 의도 확률은 0), 직전 추천에 이어지는 말일 확률
    public static JevIntentJudgement.First first(ChatIntent intent, double confidence, Double followsPrevious) {
        Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
        Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, each == intent ? confidence : 0.0));
        return first(intent, confidence, probabilities, followsPrevious);
    }

    // 1차: 의도와 confidence, 의도별 확률
    public static JevIntentJudgement.First first(ChatIntent intent, double confidence, Map<ChatIntent, Double> probabilities,
                                                 Double followsPrevious) {
        return new JevIntentJudgement.First(intent, intent, confidence, probabilities, followsPrevious, 0.05, FridgeScope.ALL, 0.9,
                call("req_first"));
    }

    // 2차: 요청 종류·1등 분류와 그 확률, 맛별 답 (없는 맛은 언급 없음)
    public static JevIntentJudgement.Second second(RequestKind kind, String category, double categoryConfidence,
                                                   Map<Taste, TasteStance> stances) {
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste,
                new TasteAnswer(stances.getOrDefault(taste, TasteStance.NOT_MENTIONED), 0.9)));
        return new JevIntentJudgement.Second(0.1, 0.1, 0.1, 0.1, tastes, kind, 0.9, category,
                categoryConfidence, "없음", 1 - categoryConfidence, call("req_second"));
    }

    public static JevIntentJudgement.Call call(String requestId) {
        return new JevIntentJudgement.Call("jev-1.14.0", requestId, 150L, 1000);
    }

    public static Ingredient ingredient(Long id, String code, String name) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("g")
                .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}

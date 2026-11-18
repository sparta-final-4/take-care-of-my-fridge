package org.example.takecareofmyfridgebe.domain.chat;

import org.example.takecareofmyfridgebe.domain.chat.ai.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 상담 의도 판단 테스트(ChatIntentFacadeTest, IntentCorrectorTest)가 같이 쓰는 준비 코드.
 * 목과 기본 응답(알레르기 없음, 레시피 분류 목록)을 만들고, 진짜 IntentCorrector를 넣은 ChatIntentFacade를 만든다.
 * 테스트마다 새로 만든다.
 */
public class ChatIntentFixture {

    public static final Long MEMBER_ID = 1L;
    public static final Long SESSION_ID = 10L;
    public static final Long TOFU = 21L;
    public static final Long MUSHROOM = 161L;
    public static final Long CUCUMBER = 15L;

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
    public final ChatIntentFacade facade;

    public ChatIntentFixture() {
        when(allergyExclusionFinder.find(any(), any())).thenReturn(AllergyExclusionFinder.Allergens.NONE);
        facade = newFacade(newCorrector());
        when(recipeService.getClassifications())
                .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "기타", "샐러드", "양념", "음료", "찌개")));
    }

    // Jev 없이(Jev 판단이 오지 않아 OpenAI 결과로 되돌림) 정하는 facade
    public ChatIntentFacade newFacade(IntentCorrector corrector) {
        return newFacade(corrector, mock(JevIntentRunner.class));
    }

    // 주어진 Jev 호출로 정하는 facade
    public ChatIntentFacade newFacade(JevIntentRunner jevIntentRunner) {
        return newFacade(newCorrector(), jevIntentRunner);
    }

    private ChatIntentFacade newFacade(IntentCorrector corrector, JevIntentRunner jevIntentRunner) {
        return new ChatIntentFacade(extractor, recipeService, ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                WORDS, allergyExclusionFinder, corrector, jevIntentRunner, new JevIntentResolver(mock(SimilarDishFinder.class), WORDS));
    }

    public IntentCorrector newCorrector() {
        return new IntentCorrector(recipeService, ingredientMatcher, chatMessageService, WORDS);
    }

    public void givenExtracted(IntentResult result) {
        when(extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any(), any())).thenReturn(result);
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

package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentShadow;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision.Decision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.intent.JevIntentResolver;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyExclusionFinder;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture.MEMBER_ID;
import static org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture.SESSION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Jev on 모드: Jev 판단 + OpenAI가 꺼낸 값으로 의도를 정하고, Jev를 못 쓰면 보정 없는 OpenAI 결과를 쓰며, 서비스 규칙만 거친다.
// Jev 호출(JevIntentShadow)과 넓은 요청 요리 이름(SimilarDishFinder)은 흉내 낸다
class ChatIntentFacadeJevOnTest {

    private final ChatIntentFixture fixture = new ChatIntentFixture();
    private final JevIntentShadow jev = mock(JevIntentShadow.class);
    private final JevIntentShadow.Run run = mock(JevIntentShadow.Run.class);
    private final SimilarDishFinder similarDishFinder = mock(SimilarDishFinder.class);
    private ChatIntentFacade facade;

    @BeforeEach
    void setUp() {
        when(jev.on()).thenReturn(true);
        when(jev.start(any(), any(), any())).thenReturn(run);
        ChatCorrectionProperties corrections = ChatCorrectionProperties.ALL_ON;
        facade = new ChatIntentFacade(fixture.extractor, fixture.recipeService, fixture.ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                ChatIntentFixture.WORDS, fixture.allergyExclusionFinder, corrections, fixture.newCorrector(corrections), jev,
                new JevIntentResolver(similarDishFinder, ChatIntentFixture.WORDS));
    }

    @Nested
    @DisplayName("요청 종류")
    class RequestKinds {

        @Test
        @DisplayName("특정 요리면 OpenAI가 꺼낸 요리 이름 그대로 이름 검색을 하고, OpenAI 키워드·분류·맛은 버린다")
        void usesOpenAiDish_whenDish() {
            givenOpenAi(recommend("김피탕", List.of("김피탕", "탕수육", "깐풍기"), List.of("찌개"), List.of("SWEET"), null));
            givenJev(RequestKind.DISH, null, Taste.SPICY);

            ChatIntentResolution resolution = resolve("김피탕 레시피 추천해줘");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("김피탕");
            assertThat(resolution.recipeQuery().keywords()).containsExactly("김피탕");
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.DISH, List.of("김피탕")));
        }

        @Test
        @DisplayName("특정 요리이고 Jev 1등 분류가 있으면 요리 이름과 함께 그 분류를 검색 조건에 넣는다")
        void addsJevTopCategory_whenDishWithTopCategory() {
            givenOpenAi(recommend("김치찌개", List.of("김치찌개"), List.of(), List.of(), null));
            givenJev(RequestKind.DISH, "찌개");

            ChatIntentResolution resolution = resolve("김치찌개 먹고 싶어");

            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("김치찌개");
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.DISH, List.of("김치찌개")));
        }

        @Test
        @DisplayName("직전 추천 없이 \"김치찌개 말고 다른 거\"에 OpenAI가 요리 이름을 비우면 on·off 모두 김치찌개를 검색 기준으로 쓰지 않는다")
        void skipsAvoidedDish_whenOpenAiLeavesDishEmpty() {
            givenOpenAi(recommend("", List.of(), List.of(), List.of(), ""));
            givenJev(RequestKind.DISH, "찌개");
            when(similarDishFinder.findBroad(MEMBER_ID, "김치찌개 말고 다른 거 추천해줘")).thenReturn(List.of("된장찌개", "제육볶음"));

            ChatIntentResolution resolution = resolve("김치찌개 말고 다른 거 추천해줘");
            fixture.givenExtracted(recommend("", List.of(), List.of(), List.of(), ""));
            ChatIntentResolution off = fixture.facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치찌개 말고 다른 거 추천해줘"));

            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().keywords()).doesNotContain("김치찌개");
            assertThat(off.recipeQuery().keywordText()).isNull();
            assertThat(off.recipeQuery().keywords()).doesNotContain("김치찌개");
        }

        @Test
        @DisplayName("\"매운 거 말고 짬뽕 같은 거\"에 OpenAI가 요리 이름 짬뽕을 주면 on·off 모두 요리 이름 짬뽕과 피할 맛 매운맛으로 찾는다")
        void usesDishAndAvoidedTaste_whenTasteIsAvoidedBeforeDish() {
            IntentResult openAi = new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of(), List.of(), false,
                    List.of("짬뽕"), "짬뽕", "", 0, "", List.of(), List.of("SPICY"), "", "ALL");
            givenOpenAi(openAi);
            givenJev(RequestKind.DISH, null, 0.95, Map.of(Taste.SPICY, TasteStance.AVOIDED));

            ChatIntentResolution resolution = resolve("매운 거 말고 짬뽕 같은 거");
            fixture.givenExtracted(openAi);
            ChatIntentResolution off = fixture.facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고 짬뽕 같은 거"));

            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("짬뽕");
            assertThat(resolution.recipeQuery().avoidTastes()).containsExactly(Taste.SPICY);
            assertThat(off.recipeQuery().keywordText()).isEqualTo("짬뽕");
            assertThat(off.recipeQuery().avoidTastes()).containsExactly(Taste.SPICY);
        }

        @Test
        @DisplayName("넓은 요청이면 원한 표현으로 넓은 요청 요리 이름을 받아 키워드로 쓰고, 요리 이름을 말한 추천으로 보지 않는다")
        void usesBroadDishes_whenBroad() {
            givenOpenAi(recommend(null, List.of("구이", "튀김"), List.of(), List.of(), "술안주"));
            givenJev(RequestKind.BROAD, null);
            when(similarDishFinder.findBroad(MEMBER_ID, "술안주")).thenReturn(List.of("오징어볶음", "계란찜", "감자튀김"));

            ChatIntentResolution resolution = resolve("술안주 추천해줘");

            assertThat(resolution.recipeQuery().keywords()).containsExactly("오징어볶음", "계란찜", "감자튀김");
            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().preferenceText()).isEqualTo("술안주");
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("오징어볶음", "계란찜", "감자튀김")));
        }

        @Test
        @DisplayName("넓은 요청인데 OpenAI 원한 표현이 없으면 끝 문장 부호를 뺀 메시지로 요리 이름을 받는다")
        void usesMessageForBroad_whenNoPreferenceText() {
            givenOpenAi(recommend(null, null, null, null, null));
            givenJev(RequestKind.BROAD, null);
            when(similarDishFinder.findBroad(MEMBER_ID, "반찬 추천해줘")).thenReturn(List.of("계란말이"));

            ChatIntentResolution resolution = resolve("반찬 추천해줘?!");

            assertThat(resolution.recipeQuery().keywords()).containsExactly("계란말이");
            assertThat(resolution.recipeQuery().preferenceText()).isEqualTo("반찬 추천해줘");
        }

        @Test
        @DisplayName("특정 분류면 Jev 1등 분류로 찾고 OpenAI 분류·키워드는 버린다")
        void usesJevCategory_whenCategory() {
            givenOpenAi(recommend(null, List.of("김치찌개"), List.of("국"), List.of(), null));
            givenJev(RequestKind.CATEGORY, "찌개");

            ChatIntentResolution resolution = resolve("찌개 추천해줘");

            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            assertThat(resolution.recipeQuery().keywordText()).isNull();
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("찌개")));
        }

        @Test
        @DisplayName("없음이면 분류·키워드 없이 냉장고 기반으로 찾는다")
        void usesNothing_whenNone() {
            givenOpenAi(recommend(null, List.of("김치볶음밥"), List.of("밥"), List.of(), null));
            givenJev(RequestKind.NONE, null);

            ChatIntentResolution resolution = resolve("뭐 먹지");

            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            verify(similarDishFinder, never()).findBroad(any(), any());
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.NONE, List.of()));
        }

        @Test
        @DisplayName("특정 분류인데 1등 분류가 없고 밀키트를 원하는 말이 아니면 넓은 요청으로 본다")
        void treatsAsBroad_whenCategoryWithoutTopCategory() {
            givenOpenAi(recommend(null, List.of("냉면", "냉채"), List.of(), List.of(), "차가운 요리"));
            givenJev(RequestKind.CATEGORY, null);
            when(similarDishFinder.findBroad(MEMBER_ID, "차가운 요리")).thenReturn(List.of("냉면", "오이냉국"));

            ChatIntentResolution resolution = resolve("차가운 요리 추천해줘");

            assertThat(resolution.recipeQuery().keywords()).containsExactly("냉면", "오이냉국");
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("냉면", "오이냉국")));
        }

        @Test
        @DisplayName("특정 분류인데 1등 분류가 없고 밀키트를 원하는 말이면 없음으로 보고 넓은 요청 요리 이름을 받지 않는다")
        void treatsAsNone_whenCategoryWithoutTopCategoryAndMealkit() {
            givenOpenAi(recommend(null, List.of("밀키트"), List.of(), List.of(), null));
            givenJev(RequestKind.CATEGORY, null);

            ChatIntentResolution resolution = resolve("밀키트 추천해줘");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            verify(similarDishFinder, never()).findBroad(any(), any());
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.NONE, List.of()));
        }

        @Test
        @DisplayName("특정 요리인데 OpenAI가 요리 이름을 비우면 \"○○ 같은\"·첫 키워드로 채우지 않고 넓은 요청으로 간다")
        void skipsLikeDishFix_andTreatsAsBroad_whenDishIsEmpty() {
            givenOpenAi(recommend(null, List.of("김피탕", "탕수육"), List.of(), List.of(), "김피탕 같은 요리"));
            givenJev(RequestKind.DISH, null);
            when(similarDishFinder.findBroad(MEMBER_ID, "김피탕 같은 요리")).thenReturn(List.of("탕수육", "깐풍기"));

            ChatIntentResolution resolution = resolve("김피탕 같은 요리 추천해줘");

            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().keywords()).containsExactly("탕수육", "깐풍기");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("탕수육", "깐풍기")));
        }

        @Test
        @DisplayName("특정 요리인데 OpenAI 요리 이름이 메시지에 없으면 넓은 요청으로 간다")
        void treatsAsBroad_whenDishNotInMessage() {
            givenOpenAi(recommend("탕수육", null, null, null, null));
            givenJev(RequestKind.DISH, null);

            ChatIntentResolution resolution = resolve("김피탕 레시피 추천해줘");

            assertThat(resolution.recipeQuery().keywordText()).isNull();
            verify(similarDishFinder).findBroad(MEMBER_ID, "김피탕 레시피 추천해줘");
        }

        @Test
        @DisplayName("추천이 아닌 의도는 Jev 의도와 OpenAI가 꺼낸 값(냉장고 범위·이름)으로 정한다")
        void usesJevIntentWithOpenAiLookup_whenFridge() {
            givenOpenAi(new IntentResult(ChatIntent.GENERAL, List.of(), List.of(), 0, List.of("두부"), List.of(), false, List.of(), "", "",
                    0, "", List.of(), List.of(), "", "ALL"));
            givenJevFirstOnly(ChatIntent.FRIDGE, 0.95);

            ChatIntentResolution resolution = resolve("두부 아직 있어?");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.ALL);
            assertThat(resolution.lookup().names()).containsExactly("두부");
            verifyDecision(new JevDecision(Decision.JEV, null, List.of()));
        }
    }

    @Nested
    @DisplayName("OpenAI 결과로 되돌림")
    class Fallback {

        @Test
        @DisplayName("Jev가 실패하면 OpenAI 결과를 쓰고, 맛·분류 단어 같은 AI 실수 고치기 보정은 거치지 않는다")
        void usesOpenAiWithoutFixes_whenJevFails() {
            givenOpenAi(recommend(null, null, List.of("국"), List.of(), null));
            when(jev.await(run)).thenReturn(Optional.empty());

            ChatIntentResolution resolution = resolve("칼칼한 국물 요리");
            fixture.givenExtracted(recommend(null, null, List.of("국"), List.of(), null));
            ChatIntentResolution off = fixture.facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("칼칼한 국물 요리"));

            assertThat(resolution.recipeQuery().wantedTastes()).isEmpty();
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("국");
            // off 모드는 맛 단어(칼칼)·분류 묶음 말(국물)로 고친다
            assertThat(off.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(off.recipeQuery().moodCategories()).contains("국", "찌개");
            verifyDecision(JevDecision.of(Decision.OPENAI_JEV_FAILED));
            verify(similarDishFinder, never()).findBroad(any(), any());
        }

        @Test
        @DisplayName("Jev 의도 confidence가 0.79면 OpenAI 결과를 쓴다")
        void usesOpenAi_whenConfidenceBelowThreshold() {
            givenOpenAi(recommend(null, null, List.of("국"), List.of(), null));
            givenJev(RequestKind.CATEGORY, "찌개", 0.79);

            ChatIntentResolution resolution = resolve("국 추천해줘");

            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("국");
            verifyDecision(JevDecision.of(Decision.OPENAI_LOW_CONFIDENCE));
        }

        @Test
        @DisplayName("confidence가 낮아 되돌릴 때 OpenAI가 추천이고 Jev 2차가 넓은 요청이면 의도·맛은 OpenAI 것으로 두고 넓은 요청 요리 이름으로 찾는다")
        void usesBroadDishesWithOpenAiIntent_whenFallingBackWithJevBroad() {
            givenOpenAi(recommend(null, List.of("밥"), List.of("밥"), List.of("SALTY"), "주식으로 먹을 거"));
            givenJev(RequestKind.BROAD, null, 0.6, Taste.SPICY);
            when(similarDishFinder.findBroad(MEMBER_ID, "주식으로 먹을 거")).thenReturn(List.of("김치볶음밥", "칼국수"));

            ChatIntentResolution resolution = resolve("주식으로 먹을 거 추천해줘");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().keywords()).containsExactly("김치볶음밥", "칼국수");
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().preferenceText()).isEqualTo("주식으로 먹을 거");
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SALTY);
            verifyDecision(new JevDecision(Decision.OPENAI_LOW_CONFIDENCE_JEV_BROAD, RequestKind.BROAD, List.of("김치볶음밥", "칼국수")));
        }

        @Test
        @DisplayName("confidence가 낮아 되돌릴 때 Jev 2차가 특정 분류면 지금처럼 OpenAI 결과를 쓰고 넓은 요청 요리 이름을 받지 않는다")
        void usesOpenAi_whenFallingBackWithJevCategory() {
            givenOpenAi(recommend(null, null, List.of("국"), List.of(), null));
            givenJev(RequestKind.CATEGORY, "찌개", 0.6);

            ChatIntentResolution resolution = resolve("국 추천해줘");

            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("국");
            verify(similarDishFinder, never()).findBroad(any(), any());
            verifyDecision(JevDecision.of(Decision.OPENAI_LOW_CONFIDENCE));
        }

        @Test
        @DisplayName("confidence가 낮아 되돌릴 때 OpenAI가 일반 질문이면 Jev 2차가 넓은 요청이어도 넓은 요청 경로를 타지 않는다")
        void keepsOpenAiGeneral_whenFallingBackWithJevBroad() {
            givenOpenAi(new IntentResult(ChatIntent.GENERAL, List.of(), List.of(), 0, List.of(), List.of(), false, List.of(), "", "",
                    0, "", List.of(), List.of(), "", "ALL"));
            givenJev(RequestKind.BROAD, null, 0.6);

            ChatIntentResolution resolution = resolve("두부는 어떻게 보관해?");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            verify(similarDishFinder, never()).findBroad(any(), any());
            verifyDecision(JevDecision.of(Decision.OPENAI_LOW_CONFIDENCE));
        }

        @Test
        @DisplayName("Jev 의도 confidence가 0.8이면 Jev로 정한다")
        void usesJev_whenConfidenceAtThreshold() {
            givenOpenAi(recommend(null, null, List.of("국"), List.of(), null));
            givenJev(RequestKind.CATEGORY, "찌개", 0.8);

            ChatIntentResolution resolution = resolve("국 추천해줘");

            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("찌개")));
        }

        @Test
        @DisplayName("OpenAI 의도 판단이 실패하면 지금 실패 처리(선택 재료 없으면 일반 답)를 쓰고 OPENAI_FAILED로 남긴다")
        void usesExistingFallback_whenOpenAiFails() {
            when(fixture.extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any(), any())).thenThrow(new IllegalStateException("timeout"));

            ChatIntentResolution resolution = resolve("뭐 먹지");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(ChatIntentResolution.Source.FALLBACK);
            verifyDecision(JevDecision.of(Decision.OPENAI_FAILED));
        }
    }

    @Nested
    @DisplayName("서비스 규칙")
    class ServiceRules {

        private final AllergyExclusionFinder.Allergens peanut = new AllergyExclusionFinder.Allergens(List.of("땅콩"), List.of(101L, 102L));
        private final ChatContext context = new ChatContext(List.of(), null,
                List.of(new CardName(10L, "김치찌개"), new CardName(20L, "순두부찌개")));

        @Test
        @DisplayName("알레르기 재료는 Jev로 정한 추천에도 뺄 재료로 더한다")
        void addsAllergens_whenJevDecides() {
            givenOpenAi(recommend(null, null, null, null, null));
            givenJev(RequestKind.NONE, null);
            when(fixture.allergyExclusionFinder.find(eq(SESSION_ID), any())).thenReturn(peanut);

            ChatIntentResolution resolution = resolve("땅콩 알레르기 있어, 뭐 먹지");

            assertThat(resolution.recipeQuery().excludeIngredientIds()).containsExactly(101L, 102L);
            assertThat(resolution.allergens()).containsExactly("땅콩");
        }

        @Test
        @DisplayName("알레르기 재료는 OpenAI로 되돌린 추천에도 뺄 재료로 더한다")
        void addsAllergens_whenFallingBack() {
            givenOpenAi(recommend(null, null, null, null, null));
            when(jev.await(run)).thenReturn(Optional.empty());
            when(fixture.allergyExclusionFinder.find(eq(SESSION_ID), any())).thenReturn(peanut);

            ChatIntentResolution resolution = resolve("땅콩 알레르기 있어, 뭐 먹지");

            assertThat(resolution.recipeQuery().excludeIngredientIds()).containsExactly(101L, 102L);
        }

        @Test
        @DisplayName("레시피 자세히 대상은 Jev로 정했을 때도 직전 카드에서 찾는다")
        void findsDetailTarget_whenJevDecides() {
            givenOpenAi(detail(2));
            givenJevFirstOnly(ChatIntent.RECIPE_DETAIL, 0.97);

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("두 번째 거 어떻게 만들어?"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
            assertThat(resolution.targetRecipeId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("레시피 자세히 대상은 OpenAI로 되돌렸을 때도 직전 카드에서 찾는다")
        void findsDetailTarget_whenFallingBack() {
            givenOpenAi(detail(2));
            when(jev.await(run)).thenReturn(Optional.empty());

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("두 번째 거 어떻게 만들어?"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
            assertThat(resolution.targetRecipeId()).isEqualTo(20L);
        }

        private IntentResult detail(int index) {
            return new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null, index, null,
                    null, null, "HOW_TO");
        }
    }

    private ChatIntentResolution resolve(String message) {
        return facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message));
    }

    private void givenOpenAi(IntentResult result) {
        fixture.givenExtracted(result);
    }

    // OpenAI 추천 결과 (요리 이름, 키워드, 분류, 원하는 맛, 원한 표현)
    private static IntentResult recommend(String dish, List<String> keywords, List<String> categories, List<String> wanted,
                                          String preference) {
        return new IntentResult(ChatIntent.RECOMMEND, wanted, categories, null, null, null, false, keywords, dish, preference);
    }

    private void givenJev(RequestKind kind, String category, Taste... wanted) {
        givenJev(kind, category, 0.95, wanted);
    }

    // 추천 0.95(또는 confidence), 2차: 요청 종류·1등 분류, 원하는 맛
    private void givenJev(RequestKind kind, String category, double confidence, Taste... wanted) {
        Map<Taste, TasteStance> stances = new EnumMap<>(Taste.class);
        Arrays.stream(wanted).forEach(taste -> stances.put(taste, TasteStance.WANTED));
        givenJev(kind, category, confidence, stances);
    }

    // 추천 confidence, 2차: 요청 종류·1등 분류, 맛별 답 (없는 맛은 언급 없음)
    private void givenJev(RequestKind kind, String category, double confidence, Map<Taste, TasteStance> stances) {
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste,
                new TasteAnswer(stances.getOrDefault(taste, TasteStance.NOT_MENTIONED), 0.9)));
        JevIntentJudgement.Second second = new JevIntentJudgement.Second(0.1, 0.1, 0.1, 0.1, tastes, kind, 0.9, category, 0.9,
                call("req_second"));
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(first(ChatIntent.RECOMMEND, confidence), second, 300L)));
    }

    private void givenJevFirstOnly(ChatIntent intent, double confidence) {
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(first(intent, confidence), null, 200L)));
    }

    private static JevIntentJudgement.First first(ChatIntent intent, double confidence) {
        Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
        Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, each == intent ? confidence : 0.0));
        return new JevIntentJudgement.First(intent, intent, confidence, probabilities, 0.05, FridgeScope.ALL, 0.9, call("req_first"));
    }

    private static JevIntentJudgement.Call call(String requestId) {
        return new JevIntentJudgement.Call("jev-1.14.0", requestId, 150L, 1000);
    }

    // 비교 로그에 넘긴 결정
    private void verifyDecision(JevDecision expected) {
        ArgumentCaptor<JevDecision> decision = ArgumentCaptor.forClass(JevDecision.class);
        verify(jev).compareWhenDone(eq(run), eq(MEMBER_ID), eq(SESSION_ID), anyInt(), any(), anyBoolean(), decision.capture());
        assertThat(decision.getValue()).isEqualTo(expected);
    }
}

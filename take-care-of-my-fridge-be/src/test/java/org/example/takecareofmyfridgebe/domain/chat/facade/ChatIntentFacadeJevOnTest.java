package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision.Decision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Jev 판단 + OpenAI가 꺼낸 값으로 의도를 정하고(confidence와 상관없이 Jev 1등 의도), AI가 실패하면 실패 처리로 답하며, 서비스 규칙만 거친다.
// Jev 호출(JevIntentRunner)과 넓은 요청 요리 이름(SimilarDishFinder)은 흉내 낸다
class ChatIntentFacadeJevOnTest {

    private final ChatIntentFixture fixture = new ChatIntentFixture();
    private final JevIntentRunner jev = mock(JevIntentRunner.class);
    private final JevIntentRunner.Run run = mock(JevIntentRunner.Run.class);
    private final SimilarDishFinder similarDishFinder = mock(SimilarDishFinder.class);
    private ChatIntentFacade facade;

    @BeforeEach
    void setUp() {
        when(jev.start(any(), any(), any())).thenReturn(run);
        facade = new ChatIntentFacade(fixture.extractor, fixture.recipeService, fixture.ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                ChatIntentFixture.WORDS, fixture.allergyExclusionFinder, fixture.newCorrector(), jev,
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
        @DisplayName("직전 추천 없이 \"김치찌개 말고 다른 거\"에 OpenAI가 요리 이름을 비우면 김치찌개를 검색 기준으로 쓰지 않는다")
        void skipsAvoidedDish_whenOpenAiLeavesDishEmpty() {
            givenOpenAi(recommend("", List.of(), List.of(), List.of(), ""));
            givenJev(RequestKind.DISH, "찌개");
            when(similarDishFinder.findBroad(MEMBER_ID, "김치찌개 말고 다른 거 추천해줘")).thenReturn(List.of("된장찌개", "제육볶음"));

            ChatIntentResolution resolution = resolve("김치찌개 말고 다른 거 추천해줘");

            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().keywords()).doesNotContain("김치찌개");
        }

        @Test
        @DisplayName("\"매운 거 말고 짬뽕 같은 거\"에 OpenAI가 요리 이름 짬뽕을 주면 요리 이름 짬뽕과 Jev 피할 맛 매운맛으로 찾는다")
        void usesDishAndAvoidedTaste_whenTasteIsAvoidedBeforeDish() {
            IntentResult openAi = new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of(), List.of(), false,
                    List.of("짬뽕"), "짬뽕", "", 0, "", List.of(), List.of("SPICY"), "", "ALL");
            givenOpenAi(openAi);
            givenJev(RequestKind.DISH, null, 0.95, Map.of(Taste.SPICY, TasteStance.AVOIDED));

            ChatIntentResolution resolution = resolve("매운 거 말고 짬뽕 같은 거");

            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("짬뽕");
            assertThat(resolution.recipeQuery().avoidTastes()).containsExactly(Taste.SPICY);
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
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("냉면", "오이냉국"), Reason.NO_TOP_CATEGORY));
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
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.NONE, List.of(), Reason.NO_TOP_CATEGORY));
        }

        @Test
        @DisplayName("특정 요리인데 OpenAI가 요리 이름을 비우고 키워드에만 넣었으면 메시지에 글자 그대로 있는 첫 키워드를 요리 이름으로 줍는다")
        void picksKeywordInMessageAsDish_whenDishIsEmpty() {
            givenOpenAi(recommend(null, List.of("탕수육", "김피탕"), List.of(), List.of(), "김피탕 같은 요리"));
            givenJev(RequestKind.DISH, null);

            ChatIntentResolution resolution = resolve("김피탕 같은 요리 추천해줘");

            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("김피탕");
            assertThat(resolution.recipeQuery().keywords()).containsExactly("김피탕");
            verify(similarDishFinder, never()).findBroad(anyLong(), anyString());
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.DISH, List.of("김피탕"), Reason.DISH_FROM_KEYWORD));
        }

        @Test
        @DisplayName("특정 요리인데 OpenAI가 요리 이름을 비우고 키워드가 모두 메시지에 없는 비슷한 요리면 줍지 않고 넓은 요청으로 간다")
        void skipsKeywordsNotInMessage_whenDishIsEmpty() {
            givenOpenAi(recommend(null, List.of("탕수육", "깐풍기"), List.of(), List.of(), "김피탕 같은 요리"));
            givenJev(RequestKind.DISH, null);
            when(similarDishFinder.findBroad(MEMBER_ID, "김피탕 같은 요리")).thenReturn(List.of("탕수육", "깐풍기"));

            ChatIntentResolution resolution = resolve("김피탕 같은 요리 추천해줘");

            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().keywords()).containsExactly("탕수육", "깐풍기");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("탕수육", "깐풍기"), Reason.NO_DISH));
        }

        @Test
        @DisplayName("특정 요리인데 OpenAI 요리 이름이 분류 이름과 같으면 요리 이름으로 쓰지 않고 그 분류로 찾는다")
        void usesCategory_whenDishIsCategoryName() {
            givenOpenAi(recommend("국", List.of("국"), List.of(), List.of(), null));
            givenJev(RequestKind.DISH, "찌개");

            ChatIntentResolution resolution = resolve("속이 안 좋아서 국 먹고 싶어");

            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("국");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("국"), Reason.DISH_IS_CATEGORY));
        }

        @Test
        @DisplayName("특정 요리인데 OpenAI가 요리 이름을 비우고 Jev 1등 분류가 있으면 특정 분류 경로로 그 분류와 맛·재료를 넘기고 넓은 요청 요리 이름을 받지 않는다")
        void usesTopCategory_whenDishIsEmptyWithTopCategory() {
            givenOpenAi(recommend(null, List.of("냉면", "막국수"), List.of("면류", "국"), List.of(), null));
            givenJev(RequestKind.DISH, "면류", Taste.MILD);

            ChatIntentResolution resolution = resolve("차가운 면");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().keywordText()).isNull();
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("면류");
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.MILD);
            verify(similarDishFinder, never()).findBroad(anyLong(), anyString());
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("면류"), Reason.DISH_TO_TOP_CATEGORY));
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
    @DisplayName("1등 분류 확률 기준")
    class TopCategoryConfidence {

        @Test
        @DisplayName("특정 요리이고 1등 분류 확률이 기준(0.7) 이상이면 요리 이름과 함께 그 분류를 넣는다")
        void addsTopCategory_whenDishAndConfidenceAtThreshold() {
            givenOpenAi(recommend("김치찌개", List.of("김치찌개"), List.of(), List.of(), null));
            givenJevWithCategoryConfidence(RequestKind.DISH, "찌개", 0.7);

            ChatIntentResolution resolution = resolve("김치찌개 먹고 싶어");

            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("김치찌개");
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.DISH, List.of("김치찌개")));
        }

        @Test
        @DisplayName("특정 요리이고 1등 분류 확률이 기준 미만이면 분류 없이 요리 이름으로만 찾는다")
        void skipsTopCategory_whenDishAndConfidenceBelowThreshold() {
            givenOpenAi(recommend("김피탕", List.of("김피탕"), List.of(), List.of(), null));
            givenJevWithCategoryConfidence(RequestKind.DISH, "국", 0.69);

            ChatIntentResolution resolution = resolve("김피탕 레시피 추천해줘");

            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("김피탕");
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.DISH, List.of("김피탕"), Reason.LOW_CATEGORY_CONFIDENCE));
        }

        @Test
        @DisplayName("특정 분류이고 1등 분류 확률이 기준 이상이면 그 분류로 찾는다")
        void usesTopCategory_whenCategoryAndConfidenceAtThreshold() {
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), null));
            givenJevWithCategoryConfidence(RequestKind.CATEGORY, "찌개", 0.7);

            ChatIntentResolution resolution = resolve("찌개 추천해줘");

            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            verify(similarDishFinder, never()).findBroad(anyLong(), anyString());
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("찌개")));
        }

        @Test
        @DisplayName("특정 분류이고 1등 분류 확률이 기준 미만이면 넓은 요청으로 간다")
        void treatsAsBroad_whenCategoryAndConfidenceBelowThreshold() {
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), "국물 요리"));
            givenJevWithCategoryConfidence(RequestKind.CATEGORY, "국", 0.69);
            when(similarDishFinder.findBroad(MEMBER_ID, "국물 요리")).thenReturn(List.of("된장찌개", "미역국"));

            ChatIntentResolution resolution = resolve("국물 요리 추천해줘");

            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            assertThat(resolution.recipeQuery().keywords()).containsExactly("된장찌개", "미역국");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("된장찌개", "미역국"), Reason.LOW_CATEGORY_CONFIDENCE));
        }

        @Test
        @DisplayName("특정 요리인데 요리 이름이 없고 1등 분류 확률이 기준 이상이면 그 분류로 찾는다")
        void usesTopCategory_whenNoDishAndConfidenceAtThreshold() {
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), null));
            givenJevWithCategoryConfidence(RequestKind.DISH, "찌개", 0.7);

            ChatIntentResolution resolution = resolve("얼큰한 거 하나");

            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("찌개"), Reason.DISH_TO_TOP_CATEGORY));
        }

        @Test
        @DisplayName("특정 요리인데 요리 이름이 없고 1등 분류 확률이 기준 미만이면 넓은 요청으로 간다")
        void treatsAsBroad_whenNoDishAndConfidenceBelowThreshold() {
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), "얼큰한 거"));
            givenJevWithCategoryConfidence(RequestKind.DISH, "국", 0.69);
            when(similarDishFinder.findBroad(MEMBER_ID, "얼큰한 거")).thenReturn(List.of("육개장", "짬뽕"));

            ChatIntentResolution resolution = resolve("얼큰한 거 하나");

            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
            assertThat(resolution.recipeQuery().keywords()).containsExactly("육개장", "짬뽕");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("육개장", "짬뽕"), Reason.LOW_CATEGORY_CONFIDENCE));
        }
    }

    @Nested
    @DisplayName("이어짐 (직전 조건을 이어받는 추천)")
    class Follows {

        // 직전 조건: 두부를 꼭 쓰는 짭짤한 국·찌개, 오이 빼기, 30분
        private final RecipeQuery previous = new RecipeQuery(MEMBER_ID, List.of(ChatIntentFixture.TOFU), List.of(Taste.SALTY),
                List.of("국", "찌개"), 30, List.of(ChatIntentFixture.CUCUMBER), true, List.of("김치찌개"), "김치찌개", null);
        private final ChatContext context = new ChatContext(List.of(), LastRecommendation.of(previous, List.of(10L, 20L)),
                List.of(new CardName(10L, "김치찌개"), new CardName(20L, "순두부찌개")));

        @Test
        @DisplayName("직전 추천이 있고 Jev가 추천 + 이어짐 0.8이면 이어짐 예로 직전 조건(분류·재료·뺄 재료·시간)을 이어받고 이번 맛을 더한다")
        void followsPreviousConditions_whenJevFollowsPrevious() {
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), null));
            givenJevFollowing(0.8, Taste.SPICY);

            ChatIntentResolution resolution = resolve("좀 더 매운 걸로", context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.follows()).isTrue();
            assertThat(resolution.recommends()).isTrue();
            RecipeQuery query = resolution.recipeQuery();
            assertThat(query.wantedTastes()).containsExactly(Taste.SALTY, Taste.SPICY);
            assertThat(query.moodCategories()).containsExactly("국", "찌개");
            assertThat(query.keywords()).containsExactly("김치찌개");
            assertThat(query.selectedIngredientIds()).containsExactly(ChatIntentFixture.TOFU);
            assertThat(query.excludeIngredientIds()).containsExactly(ChatIntentFixture.CUCUMBER);
            assertThat(query.maxMinutes()).isEqualTo(30);
            assertThat(query.useSelectedOnly()).isTrue();
        }

        @Test
        @DisplayName("직전 추천이 있어도 Jev가 추천 + 이어짐 0.3이면 이어짐 아니오로 직전 조건 없이 새 추천을 한다")
        void recommendsNew_whenJevDoesNotFollowPrevious() {
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), null));
            givenJevFollowing(0.3, Taste.SPICY);

            ChatIntentResolution resolution = resolve("매운 거 추천해줘", context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.follows()).isFalse();
            RecipeQuery query = resolution.recipeQuery();
            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.moodCategories()).isEmpty();
            assertThat(query.selectedIngredientIds()).isEmpty();
            assertThat(query.excludeIngredientIds()).isEmpty();
            assertThat(query.maxMinutes()).isNull();
        }

        @Test
        @DisplayName("직전 추천(직전 조건)이 없으면 Jev 이어짐 0.8이어도 이어짐 아니오로 새 추천을 한다")
        void neverFollows_whenNoLastRecommendation() {
            ChatContext withoutRedis = new ChatContext(List.of(), null, context.lastCards());
            givenOpenAi(recommend(null, List.of(), List.of(), List.of(), null));
            givenJevFollowing(0.8, Taste.SPICY);

            ChatIntentResolution byJev = resolve("좀 더 매운 걸로", withoutRedis);
            ChatIntentResolution firstQuestion = resolve("좀 더 매운 걸로");

            assertThat(byJev.follows()).isFalse();
            assertThat(firstQuestion.follows()).isFalse();
            assertThat(byJev.recipeQuery().moodCategories()).isEmpty();
            assertThat(byJev.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
        }

        @Test
        @DisplayName("Jev가 추천이 아닌 의도면 이어짐 확률이 높아도 이어짐 아니오다")
        void neverFollows_whenJevIntentIsNotRecommend() {
            givenOpenAi(IntentResult.general());
            givenJevFirstOnly(ChatIntent.GENERAL, 0.95, 0.9);

            ChatIntentResolution resolution = resolve("고마워", context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.follows()).isFalse();
        }
    }

    @Nested
    @DisplayName("Jev 1등 의도 그대로와 AI 실패")
    class JevOnly {

        @Test
        @DisplayName("Jev 의도 confidence가 0.5인 추천도 OpenAI로 되돌리지 않고 Jev 추천으로 정한다")
        void usesJevRecommend_whenConfidenceIsLow() {
            givenOpenAi(recommend(null, null, List.of("국"), List.of(), null));
            givenJev(RequestKind.CATEGORY, "찌개", 0.5);

            ChatIntentResolution resolution = resolve("국 추천해줘");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.source()).isEqualTo(ChatIntentResolution.Source.AI);
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.CATEGORY, List.of("찌개")));
        }

        @Test
        @DisplayName("Jev 의도 confidence가 낮은 넓은 요청도 Jev 맛과 넓은 요청 요리 이름으로 찾는다")
        void usesJevBroad_whenConfidenceIsLow() {
            givenOpenAi(recommend(null, List.of("밥"), List.of("밥"), List.of("SALTY"), "주식으로 먹을 거"));
            givenJev(RequestKind.BROAD, null, 0.6, Taste.SPICY);
            when(similarDishFinder.findBroad(MEMBER_ID, "주식으로 먹을 거")).thenReturn(List.of("김치볶음밥", "칼국수"));

            ChatIntentResolution resolution = resolve("주식으로 먹을 거 추천해줘");

            assertThat(resolution.recipeQuery().keywords()).containsExactly("김치볶음밥", "칼국수");
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
            verifyDecision(new JevDecision(Decision.JEV, RequestKind.BROAD, List.of("김치볶음밥", "칼국수")));
        }

        @Test
        @DisplayName("Jev 의도 confidence가 낮은 일반 질문도 그대로 일반 답으로 정한다")
        void usesJevGeneral_whenConfidenceIsLow() {
            givenOpenAi(recommend(null, null, null, null, null));
            givenJevFirstOnly(ChatIntent.GENERAL, 0.4);

            ChatIntentResolution resolution = resolve("슬슬 처리해야 할 거 있어?");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(ChatIntentResolution.Source.AI);
            verify(similarDishFinder, never()).findBroad(any(), any());
        }

        @Test
        @DisplayName("Jev가 실패하면 OpenAI 꺼낸 값을 쓰지 않고 선택 재료 서버 답으로 정하고 JEV_FAILED_SERVER_FALLBACK으로 남긴다")
        void answersBySelected_whenJevFails() {
            givenOpenAi(recommend("김치찌개", List.of("김치찌개"), List.of("찌개"), List.of(), null));
            when(jev.await(run)).thenReturn(Optional.empty());

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("김치찌개 먹고 싶어", ChatMessageMode.AUTO, List.of(ChatIntentFixture.TOFU), true));

            assertThat(resolution.serverFallback()).isTrue();
            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery()).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of(ChatIntentFixture.TOFU), true));
            verifyDecision(JevDecision.of(Decision.JEV_FAILED_SERVER_FALLBACK));
        }

        @Test
        @DisplayName("Jev가 실패하고 선택 재료가 없으면 조건 없는(냉장고 재료 기반) 서버 답으로 정한다")
        void answersByFridge_whenJevFailsWithoutSelected() {
            givenOpenAi(recommend("김치찌개", List.of("김치찌개"), List.of("찌개"), List.of(), null));
            when(jev.await(run)).thenReturn(Optional.empty());

            ChatIntentResolution resolution = resolve("김치찌개 먹고 싶어");

            assertThat(resolution.serverFallback()).isTrue();
            assertThat(resolution.recipeQuery()).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of(), false));
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            verifyDecision(JevDecision.of(Decision.JEV_FAILED_SERVER_FALLBACK));
        }

        @Test
        @DisplayName("OpenAI 꺼내기가 실패하고 Jev가 추천이면 꺼낸 값이 없어 서버 답으로 정하고 OPENAI_FAILED_SERVER_FALLBACK으로 남긴다")
        void answersByServer_whenOpenAiFailsAndJevRecommends() {
            when(fixture.extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any())).thenThrow(new IllegalStateException("timeout"));
            givenJev(RequestKind.DISH, "찌개");

            ChatIntentResolution resolution = resolve("김치찌개 먹고 싶어");

            assertThat(resolution.serverFallback()).isTrue();
            assertThat(resolution.recipeQuery()).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of(), false));
            verifyDecision(JevDecision.of(Decision.OPENAI_FAILED_SERVER_FALLBACK));
        }

        @Test
        @DisplayName("OpenAI 꺼내기가 실패해도 Jev가 일반 질문이면 꺼낸 값이 필요 없어 일반 답으로 정한다")
        void answersGeneral_whenOpenAiFailsAndJevSaysGeneral() {
            when(fixture.extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any())).thenThrow(new IllegalStateException("timeout"));
            givenJevFirstOnly(ChatIntent.GENERAL, 0.9);

            ChatIntentResolution resolution = resolve("두부는 어떻게 보관해?");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(ChatIntentResolution.Source.AI);
            assertThat(resolution.serverFallback()).isFalse();
            verifyDecision(JevDecision.of(Decision.JEV));
        }
    }

    @Nested
    @DisplayName("자해 안내 (Jev 1등 의도 또는 자해 확률 기준)")
    class SelfHarm {

        @Test
        @DisplayName("Jev 1등 의도가 자해면 자해 안내로 정한다")
        void declinesSelfHarm_whenTopIntentIsSelfHarm() {
            givenOpenAi(IntentResult.general());
            givenJevWithSelfHarm(ChatIntent.SELF_HARM, 0.66, 0.66);

            ChatIntentResolution resolution = resolve("요즘 살 이유가 없는 것 같아");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.SELF_HARM);
            assertThat(resolution.declines()).isTrue();
        }

        @Test
        @DisplayName("Jev 1등 의도가 일반 질문이어도 자해 확률이 0.35(기준 0.3 이상)면 자해 안내로 정한다")
        void declinesSelfHarm_whenSelfHarmProbabilityAboveThreshold() {
            givenOpenAi(IntentResult.general());
            givenJevWithSelfHarm(ChatIntent.GENERAL, 0.6, 0.35);

            ChatIntentResolution resolution = resolve("다 그만두고 싶어");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.SELF_HARM);
            assertThat(resolution.declines()).isTrue();
        }

        @Test
        @DisplayName("Jev 1등 의도가 일반 질문이고 자해 확률이 0.2(기준 미만)면 일반 질문으로 정한다")
        void answersGeneral_whenSelfHarmProbabilityBelowThreshold() {
            givenOpenAi(IntentResult.general());
            givenJevWithSelfHarm(ChatIntent.GENERAL, 0.75, 0.2);

            ChatIntentResolution resolution = resolve("배고파 죽겠어");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.declines()).isFalse();
        }

        @Test
        @DisplayName("OpenAI 꺼내기가 실패해도 Jev 1등 의도가 자해면 자해 안내로 정한다")
        void declinesSelfHarm_whenOpenAiFailsAndJevSaysSelfHarm() {
            when(fixture.extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any())).thenThrow(new IllegalStateException("timeout"));
            givenJevWithSelfHarm(ChatIntent.SELF_HARM, 0.7, 0.7);

            ChatIntentResolution resolution = resolve("요즘 살 이유가 없는 것 같아");

            assertThat(resolution.intent()).isEqualTo(ChatIntent.SELF_HARM);
            assertThat(resolution.declines()).isTrue();
        }

        // 1차만: 1등 의도와 confidence, 자해 확률 (1등 의도 확률은 confidence)
        private void givenJevWithSelfHarm(ChatIntent intent, double confidence, double selfHarm) {
            Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
            Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, 0.0));
            probabilities.put(intent, confidence);
            probabilities.put(ChatIntent.SELF_HARM, selfHarm);
            when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(
                    ChatIntentFixture.first(intent, confidence, probabilities, null), null, 200L)));
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
        @DisplayName("레시피 자세히 대상은 Jev로 정했을 때도 직전 카드에서 찾는다")
        void findsDetailTarget_whenJevDecides() {
            givenOpenAi(detail(2));
            givenJevFirstOnly(ChatIntent.RECIPE_DETAIL, 0.97);

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

    private ChatIntentResolution resolve(String message, ChatContext context) {
        return facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message), context);
    }

    // OpenAI가 꺼낸 값 (결과의 의도·이어짐·맛·분류는 OpenAI가 내지 않으므로 버린다)
    private void givenOpenAi(IntentResult result) {
        fixture.givenExtracted(ChatIntentFixture.extractionOf(result));
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

    // 추천 confidence, 2차: 요청 종류·1등 분류(확률 0.9), 맛별 답 (없는 맛은 언급 없음)
    private void givenJev(RequestKind kind, String category, double confidence, Map<Taste, TasteStance> stances) {
        givenJevWithCategoryConfidence(kind, category, 0.9, confidence, stances);
    }

    // 추천 0.95, 2차: 요청 종류·1등 분류와 그 확률
    private void givenJevWithCategoryConfidence(RequestKind kind, String category, double categoryConfidence) {
        givenJevWithCategoryConfidence(kind, category, categoryConfidence, 0.95, Map.of());
    }

    private void givenJevWithCategoryConfidence(RequestKind kind, String category, double categoryConfidence, double confidence,
                                                Map<Taste, TasteStance> stances) {
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste,
                new TasteAnswer(stances.getOrDefault(taste, TasteStance.NOT_MENTIONED), 0.9)));
        JevIntentJudgement.Second second = new JevIntentJudgement.Second(0.1, 0.1, 0.1, 0.1, tastes, kind, 0.9, category,
                categoryConfidence, "없음", 1 - categoryConfidence, call("req_second"));
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(first(ChatIntent.RECOMMEND, confidence), second, 300L)));
    }

    private void givenJevFirstOnly(ChatIntent intent, double confidence) {
        givenJevFirstOnly(intent, confidence, null);
    }

    // 1차만 (의도·confidence, 직전 추천에 이어지는 말일 확률)
    private void givenJevFirstOnly(ChatIntent intent, double confidence, Double followsPrevious) {
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(first(intent, confidence, followsPrevious), null, 200L)));
    }

    // 추천 0.95와 이어짐 확률, 2차: 요청 종류 없음, 원하는 맛
    private void givenJevFollowing(double followsPrevious, Taste... wanted) {
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste, new TasteAnswer(
                Arrays.asList(wanted).contains(taste) ? TasteStance.WANTED : TasteStance.NOT_MENTIONED, 0.9)));
        JevIntentJudgement.Second second = new JevIntentJudgement.Second(0.1, 0.1, 0.1, 0.1, tastes, RequestKind.NONE, 0.9, null,
                0.0, "없음", 1.0, call("req_second"));
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(
                first(ChatIntent.RECOMMEND, 0.95, followsPrevious), second, 300L)));
    }

    private static JevIntentJudgement.First first(ChatIntent intent, double confidence) {
        return first(intent, confidence, null);
    }

    private static JevIntentJudgement.First first(ChatIntent intent, double confidence, Double followsPrevious) {
        Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
        Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, each == intent ? confidence : 0.0));
        return new JevIntentJudgement.First(intent, intent, confidence, probabilities, followsPrevious, 0.05, FridgeScope.ALL, 0.9,
                call("req_first"));
    }

    private static JevIntentJudgement.Call call(String requestId) {
        return new JevIntentJudgement.Call("jev-1.14.0", requestId, 150L, 1000);
    }

    // 비교 로그에 넘긴 결정
    private void verifyDecision(JevDecision expected) {
        ArgumentCaptor<JevDecision> decision = ArgumentCaptor.forClass(JevDecision.class);
        verify(jev).logWhenDone(eq(run), eq(MEMBER_ID), eq(SESSION_ID), anyInt(), any(), anyBoolean(), decision.capture());
        assertThat(decision.getValue()).isEqualTo(expected);
    }
}

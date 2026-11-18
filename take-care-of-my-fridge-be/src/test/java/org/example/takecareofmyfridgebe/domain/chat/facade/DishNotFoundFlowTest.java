package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishes;
import org.example.takecareofmyfridgebe.domain.chat.safety.SafetyWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture.MEMBER_ID;
import static org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture.SESSION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 요리 이름만(또는 요리 이름 + 레시피·추천 말) 말했는데 검색이 모두 0건이고 비슷한 요리도 없을 때, 의도 판단부터 추천까지 이어서 본다.
 * 빈 카드 목록만 나가지 않고 요리 이름을 넣은 "아직 레시피가 없다" 안내(서버 글, RecipeCandidateCollector.explainEmpty)가 나가야 한다.
 * 이 안내(fixedReply)를 ChatStreamFacade가 AI 없이 그대로 보내는 것은 ChatStreamFacadeTest가 본다.
 * AI(OpenAI 의도 판단·Jev)와 검색(ES 후보·글자 검색·비슷한 요리)은 흉내 내고, 의도 판단·후보 고르기·추천 답은 실제 객체를 쓴다.
 */
class DishNotFoundFlowTest {

    private final ChatIntentFixture fixture = new ChatIntentFixture();
    private final JevIntentRunner jev = mock(JevIntentRunner.class);
    private final JevIntentRunner.Run run = mock(JevIntentRunner.Run.class);
    private final RecipeService recipeService = mock(RecipeService.class);
    private final RecipeSearchService recipeSearchService = mock(RecipeSearchService.class);
    private final RecipeCandidateSearchService candidateSearchService = mock(RecipeCandidateSearchService.class);
    private final SimilarDishFinder similarDishFinder = mock(SimilarDishFinder.class);
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private ChatIntentFacade intentFacade;
    private ChatRecommendationFacade recommendationFacade;

    @BeforeEach
    void setUp() throws Exception {
        when(jev.start(any(), any(), any())).thenReturn(run);
        intentFacade = fixture.newFacade(jev);

        // 검색은 모두 0건: ES 후보·글자 검색·벡터 검색, 레시피 이름 조회도 빈 결과. 비슷한 요리도 없다
        when(candidateSearchService.search(any())).thenReturn(List.of());
        when(recipeSearchService.search(any(RecipeListRequest.class))).thenReturn(new RecipeSearchResult(List.of(), 0, true));
        when(recipeSearchService.searchKeywordOnly(any(RecipeListRequest.class))).thenReturn(new RecipeSearchResult(List.of(), 0));
        when(recipeService.findCandidateInfos(any())).thenReturn(Map.of());
        when(similarDishFinder.find(any(), any())).thenReturn(SimilarDishes.NONE);
        MemberService memberService = mock(MemberService.class);
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        FridgeService fridgeService = mock(FridgeService.class);
        when(fridgeService.getItems(any(), any())).thenReturn(List.of());
        IngredientService ingredientService = mock(IngredientService.class);
        when(ingredientService.getIngredientsByIds(any())).thenReturn(Map.of());

        RecipeSearchProperties searchProperties = new RecipeSearchProperties(SearchEngine.ELASTICSEARCH, null, null);
        RecipeCandidateCollector collector = new RecipeCandidateCollector(recipeService, recipeSearchService,
                candidateSearchService, searchProperties, RecipeCandidateFacadeTest.CANDIDATE_PROPERTIES);
        RecipeTasteReader tasteReader = new RecipeTasteReader(recipeService, candidateSearchService, searchProperties,
                RecipeCandidateFacadeTest.CANDIDATE_PROPERTIES);
        RecipeCandidateFacade candidateFacade = new RecipeCandidateFacade(fridgeService, memberService, recipeService,
                ingredientService, RecipeCandidateFacadeTest.CANDIDATE_PROPERTIES, collector, tasteReader, similarDishFinder);
        recommendationFacade = new ChatRecommendationFacade(candidateFacade, mock(RecipeCardDescriber.class), recipeService,
                ingredientService, mock(ProductService.class), mock(SaleEventReader.class), chatMessageService,
                ChatIntentFixture.WORDS, SafetyWordCheckerTest.ALL);
    }

    @Test
    @DisplayName("요리 이름만 말한 \"김피탕\"이 검색 0건이면 빈 카드와 함께 김피탕 레시피가 아직 없다는 안내를 보낸다")
    void sendsNotFoundNotice_whenOnlyDishNameFindsNothing() {
        givenOpenAi(aiRecommend("김피탕"));
        givenJevRecommendDish();

        RecommendationReply reply = recommend("김피탕");

        assertThat(reply.event().empty()).isTrue();
        assertThat(reply.fixedReply()).isEqualTo("김피탕 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.");
    }

    @Test
    @DisplayName("\"김피탕 레시피\"에서 AI가 요리 이름을 김피탕으로 꺼내고 검색이 0건이면 김피탕 레시피가 아직 없다는 안내를 보낸다")
    void sendsNotFoundNotice_whenDishWithRecipeWordFindsNothing() {
        givenOpenAi(aiRecommend("김피탕"));
        givenJevRecommendDish();

        RecommendationReply reply = recommend("김피탕 레시피");

        assertThat(reply.event().empty()).isTrue();
        assertThat(reply.fixedReply()).isEqualTo("김피탕 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.");
    }

    @Test
    @DisplayName("\"없는요리이름 추천해줘\"가 검색 0건이면 그 요리 이름으로 아직 없다는 안내를 보낸다")
    void sendsNotFoundNotice_whenUnknownDishRecommendFindsNothing() {
        givenOpenAi(aiRecommend("없는요리이름"));
        givenJevRecommendDish();

        RecommendationReply reply = recommend("없는요리이름 추천해줘");

        assertThat(reply.event().empty()).isTrue();
        assertThat(reply.fixedReply()).isEqualTo("없는요리이름 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.");
    }

    @Test
    @DisplayName("Jev를 못 써 OpenAI 결과로 되돌린 \"김피탕\"도 검색 0건이면 김피탕 레시피가 아직 없다는 안내를 보낸다")
    void sendsNotFoundNotice_whenFallbackToOpenAiFindsNothing() {
        givenOpenAi(aiRecommend("김피탕"));
        when(jev.await(run)).thenReturn(Optional.empty());

        RecommendationReply reply = recommend("김피탕");

        assertThat(reply.event().empty()).isTrue();
        assertThat(reply.fixedReply()).isEqualTo("김피탕 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.");
    }

    @Test
    @DisplayName("AI가 \"김피탕\"을 일반 대화로 보면 추천 검색을 하지 않고 일반 답으로 간다")
    void goesGeneral_whenAiSeesDishNameAsGeneral() throws Exception {
        fixture.givenExtracted(new IntentResult(ChatIntent.GENERAL, List.of(), List.of(), null, null, null, false,
                List.of(), null, null));
        givenJevFirst(ChatIntent.GENERAL);

        ChatIntentResolution resolution = resolve("김피탕");

        assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
        assertThat(resolution.recommends()).isFalse();
        verify(candidateSearchService, never()).search(any());
    }

    // 의도 판단 → 추천 (ChatStreamFacade.planReply와 같은 순서: 추천이면 이미 추천한 레시피를 붙여 추천 facade로)
    private RecommendationReply recommend(String message) {
        ChatIntentResolution resolution = resolve(message);
        assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(resolution.recommends()).isTrue();
        return recommendationFacade.recommend(message,
                resolution.recipeQuery().withAlreadyRecommended(chatMessageService.findRecommendedRecipeIds(SESSION_ID)),
                List.of(), SESSION_ID);
    }

    private ChatIntentResolution resolve(String message) {
        return intentFacade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message));
    }

    private void givenOpenAi(IntentResult result) {
        fixture.givenExtracted(result);
    }

    // OpenAI 추천 결과: 요리 이름과 그 이름 하나뿐인 키워드
    private static IntentResult aiRecommend(String dish) {
        return new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), null, null, null, false, List.of(dish), dish, null);
    }

    // Jev: 추천 0.95, 2차는 특정 요리(1등 분류 없음), 맛 언급 없음
    private void givenJevRecommendDish() {
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste, new TasteAnswer(TasteStance.NOT_MENTIONED, 0.9)));
        JevIntentJudgement.Second second = new JevIntentJudgement.Second(0.1, 0.1, 0.1, 0.1, tastes, RequestKind.DISH, 0.9,
                null, 0.0, "없음", 1.0, call("req_second"));
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(first(ChatIntent.RECOMMEND), second, 300L)));
    }

    private void givenJevFirst(ChatIntent intent) {
        when(jev.await(run)).thenReturn(Optional.of(new JevIntentJudgement(first(intent), null, 200L)));
    }

    private static JevIntentJudgement.First first(ChatIntent intent) {
        Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
        Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, each == intent ? 0.95 : 0.0));
        return new JevIntentJudgement.First(intent, intent, 0.95, probabilities, null, 0.05, FridgeScope.ALL, 0.9, call("req_first"));
    }

    private static JevIntentJudgement.Call call(String requestId) {
        return new JevIntentJudgement.Call("jev-1.14.0", requestId, 150L, 1000);
    }
}

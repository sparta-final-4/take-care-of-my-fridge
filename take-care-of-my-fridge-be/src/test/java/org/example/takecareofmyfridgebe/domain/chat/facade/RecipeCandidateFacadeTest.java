package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.CandidateHit;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.Condition;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class RecipeCandidateFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private MemberService memberService;
    private FridgeService fridgeService;
    private RecipeService recipeService;
    private IngredientService ingredientService;
    private RecipeSearchService recipeSearchService;
    private RecipeCandidateSearchService candidateSearchService;
    // DB 경로 (ES를 쓰지 않는 설정). 아래 테스트 대부분은 DB 경로의 거르기·정렬을 확인한다
    private RecipeCandidateFacade facade;
    // ES 후보 검색 경로
    private RecipeCandidateFacade esFacade;

    @BeforeEach
    void setUp() {
        fridgeService = mock(FridgeService.class);
        memberService = mock(MemberService.class);
        recipeService = mock(RecipeService.class);
        ingredientService = mock(IngredientService.class);
        recipeSearchService = mock(RecipeSearchService.class);
        candidateSearchService = mock(RecipeCandidateSearchService.class);
        facade = facadeWith(SearchEngine.MYSQL);
        esFacade = facadeWith(SearchEngine.ELASTICSEARCH);
        when(fridgeService.getItems(any(), any())).thenReturn(List.of());
    }

    private RecipeCandidateFacade facadeWith(SearchEngine engine) {
        return new RecipeCandidateFacade(fridgeService, memberService, recipeService, ingredientService, recipeSearchService,
                candidateSearchService, new RecipeSearchProperties(engine, null, null),
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4,
                List.of("청양고추", "고춧가루", "고추장", "고추기름", "건고추", "베트남고추", "페퍼론치노", "핫소스", "김치", "깍두기"),
                List.of("백김치", "물김치", "동치미", "나박김치"), List.of("홍고추", "청고추")));
    }

    @Test
    @DisplayName("원하는 맛을 말했으면 회원 선호 맛을 조회하지 않는다")
    void skipsMemberTastes_whenWantedTastesExist() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), null, null, null, false);

        facade.findCandidates(query);

        verify(memberService, never()).getMyInfo(any());
    }

    @Test
    @DisplayName("원하는 맛을 말하지 않았으면 회원 선호 맛을 조회한다")
    void readsMemberTastes_whenNoWantedTastes() {
        MyInfoResponse myInfo = mock(MyInfoResponse.class);
        when(myInfo.tastes()).thenReturn(List.of(Taste.SALTY));
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(myInfo);

        assertThat(facade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of())).candidates()).isEmpty();

        verify(memberService).getMyInfo(MEMBER_ID);
    }

    @Test
    @DisplayName("선택 재료의 같은 종류 재료(버섯 → 표고버섯)로도 레시피 후보를 찾는다")
    void looksUpSameKindIngredients_whenSelected() {
        Ingredient mushroom = Ingredient.builder().ingredientCode("ING_RCP_0045").name("버섯").baseUnit("g").build();
        ReflectionTestUtils.setField(mushroom, "id", 45L);
        when(ingredientService.getIngredientsByIds(List.of(45L))).thenReturn(Map.of(45L, mushroom));
        when(ingredientService.findSameKindIdsByNames(List.of("버섯"))).thenReturn(Map.of("버섯", List.of(45L, 161L)));
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));

        facade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of(45L), true));

        verify(recipeService).findCandidateIngredients(Set.of(45L, 161L));
    }

    @Test
    @DisplayName("키워드마다 레시피 검색으로 레시피를 찾아 재료까지 받고, 이름이 맞은 레시피가 있으면 못 찾음이 아니다")
    void searchesRecipesByKeywords() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeSearchService.search(any(RecipeListRequest.class))).thenAnswer(invocation -> {
            RecipeListRequest request = invocation.getArgument(0);
            return switch (request.keyword()) {
                case "냉면" -> new RecipeSearchResult(List.of(20L, 30L), 2);
                case "막국수" -> new RecipeSearchResult(List.of(30L), 1);
                default -> new RecipeSearchResult(List.of(), 0);
            };
        });
        when(recipeService.findCandidateInfos(Set.of(20L, 30L))).thenReturn(Map.of(
                20L, new RecipeCandidateInfo(20L, "물냉면", "면류", 30, 5),
                30L, new RecipeCandidateInfo(30L, "들기름 막국수", "면류", 20, 4)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("냉면", "막국수"), "냉면");

        RecipeCandidates result = facade.findCandidates(query);

        verify(recipeService).findNonBasicIngredients(Set.of(20L, 30L));
        assertThat(result.keywordsNotFound()).isFalse();
    }

    @Test
    @DisplayName("키워드로 찾은 레시피가 없으면 keywordsNotFound가 true이고, 검색이 실패한 키워드는 건너뛴다")
    void marksKeywordsNotFound_whenNoRecipeFound() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeSearchService.search(any(RecipeListRequest.class)))
                .thenThrow(new IOException("es down"))
                .thenReturn(new RecipeSearchResult(List.of(), 0));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("냉파스타", "냉메밀"), "냉파스타");

        RecipeCandidates result = facade.findCandidates(query);

        assertThat(result.keywordsNotFound()).isTrue();
    }

    @Test
    @DisplayName("키워드가 없으면 레시피 검색을 부르지 않는다")
    void skipsSearch_whenNoKeywords() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));

        RecipeCandidates result = facade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of()));

        verify(recipeSearchService, never()).search(any());
        assertThat(result.keywordsNotFound()).isFalse();
    }

    @Test
    @DisplayName("벡터 검색으로만 찾은 레시피뿐이면 이름·재료가 맞은 레시피가 없어 keywordsNotFound가 true다")
    void marksKeywordsNotFound_whenOnlyVectorFound() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeSearchService.search(any(RecipeListRequest.class)))
                .thenReturn(new RecipeSearchResult(List.of(40L), 1, true));
        when(recipeService.findCandidateInfos(Set.of(40L)))
                .thenReturn(Map.of(40L, new RecipeCandidateInfo(40L, "들깨 칼국수", "면류", 30, 5)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("냉파스타"), "냉파스타");

        RecipeCandidates result = facade.findCandidates(query);

        assertThat(result.keywordsNotFound()).isTrue();
    }

    @Test
    @DisplayName("저장된 레시피를 지금 냉장고 기준으로 다시 계산해 냉장고가 바뀌면 임박·장볼 재료도 바뀌고, 조회는 한 번씩이다")
    void describesRecipesWithCurrentFridge() {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeService.findNonBasicIngredients(List.of(30L, 40L))).thenReturn(List.of(
                new RecipeCandidateIngredient(30L, 1L, "두부", false),
                new RecipeCandidateIngredient(30L, 2L, "양파", false),
                new RecipeCandidateIngredient(40L, 3L, "장어", false)));
        when(recipeService.findCandidateInfos(List.of(30L, 40L))).thenReturn(Map.of(
                30L, new RecipeCandidateInfo(30L, "두부조림", "조림", 20, 2),
                40L, new RecipeCandidateInfo(40L, "장어조림", "조림", 30, 1)));
        when(fridgeService.getItems(any(), any()))
                .thenReturn(List.of(fridgeItem(1L, "두부", 1)))
                .thenReturn(List.of(fridgeItem(1L, "두부", 5), fridgeItem(2L, "양파", 0)));

        Map<Long, RecipeCandidate> before = facade.describeRecipes(MEMBER_ID, List.of(30L, 40L));
        Map<Long, RecipeCandidate> after = facade.describeRecipes(MEMBER_ID, List.of(30L, 40L));

        assertThat(before.get(30L).reason().imminentIngredients()).containsExactly(new ImminentIngredient("두부", 1));
        assertThat(before.get(30L).reason().missingIngredients()).containsExactly(new MissingIngredient(2L, "양파"));
        assertThat(after.get(30L).reason().imminentIngredients()).containsExactly(new ImminentIngredient("양파", 0));
        assertThat(after.get(30L).reason().missingIngredients()).isEmpty();
        assertThat(after.get(40L).reason().missingIngredients()).containsExactly(new MissingIngredient(3L, "장어"));
        assertThat(after.get(30L).reason().missingSelectedIngredients()).isEmpty();
        verify(recipeService, times(2)).findNonBasicIngredients(List.of(30L, 40L));
        verify(recipeService, times(2)).findCandidateInfos(List.of(30L, 40L));
        verify(fridgeService, times(2)).getItems(any(), any());
    }

    @Test
    @DisplayName("지워진(정보가 없는) 레시피는 결과에서 빠진다")
    void skipsDeletedRecipe() {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeService.findCandidateInfos(List.of(30L, 99L)))
                .thenReturn(Map.of(30L, new RecipeCandidateInfo(30L, "두부조림", "조림", 20, 1)));

        assertThat(facade.describeRecipes(MEMBER_ID, List.of(30L, 99L))).containsOnlyKeys(30L);
    }

    @Test
    @DisplayName("\"매운 거 말고\"면 맛 데이터나 매운 재료(원문 홍고추·청고추, 김치)로 매운 레시피를 후보에서 빼고, 백김치는 남긴다")
    void dropsSpicyRecipes_whenAvoidingSpicy() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "깐풍기", Set.of(Taste.SALTY, Taste.SWEET), List.of("닭고기", "고추", "홍고추")),
                20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기")),
                30L, recipe(30L, "장어조림", Set.of(Taste.SALTY), List.of("장어", "고추", "청고추")),
                40L, recipe(40L, "닭강정", Set.of(Taste.SWEET), List.of("닭고기")),
                50L, recipe(50L, "유린기", Set.of(Taste.SPICY), List.of("닭고기")),
                60L, recipe(60L, "꿔바로우", Set.of(Taste.SWEET), List.of("돼지고기", "고추")),
                70L, recipe(70L, "두부김치", Set.of(Taste.SALTY), List.of("두부", "김치")),
                80L, recipe(80L, "백김치말이", Set.of(Taste.MILD), List.of("백김치"))));

        RecipeCandidates result = facade.findCandidates(avoidSpicy(List.of("튀김")));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(20L, 40L, 60L, 80L);
    }

    @Test
    @DisplayName("매운 레시피를 빼서 3개보다 적으면 그때만 매운 레시피를 뒤에 채운다")
    void refillsSpicyRecipesAtEnd_whenTooFew() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "깐풍기", Set.of(Taste.SALTY), List.of("홍고추")),
                20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기")),
                30L, recipe(30L, "장어조림", Set.of(Taste.SALTY), List.of("청고추"))));

        RecipeCandidates result = facade.findCandidates(avoidSpicy(List.of("튀김")));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).hasSize(3).startsWith(20L);
    }

    @Test
    @DisplayName("회원 선호 맛에 매운맛이 있어도 피할 맛이 이겨 매운맛을 세지 않고 매운 레시피를 뺀다")
    void avoidWinsOverMemberTaste() throws Exception {
        MyInfoResponse myInfo = mock(MyInfoResponse.class);
        when(myInfo.tastes()).thenReturn(List.of(Taste.SPICY, Taste.SWEET));
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(myInfo);
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "깐풍기", Set.of(Taste.SWEET), List.of("홍고추")),
                20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기")),
                30L, recipe(30L, "닭강정", Set.of(Taste.SWEET), List.of("닭고기")),
                40L, recipe(40L, "꿔바로우", Set.of(Taste.SWEET), List.of("돼지고기"))));

        RecipeCandidates result = facade.findCandidates(avoidSpicy(null));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).doesNotContain(10L);
        assertThat(result.candidates()).allSatisfy(candidate ->
                assertThat(candidate.reason().matchedTastes()).doesNotContain(Taste.SPICY));
    }

    @Test
    @DisplayName("\"매운 거 먹고 싶어\"도 같은 기준: 맛 데이터에 매운맛이 없어도 재료에 고춧가루가 있으면 원하는 맛과 맞은 것으로 센다")
    void countsSpicyIngredient_asWantedSpicy() throws Exception {
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "깻잎말이김치", Set.of(Taste.SALTY, Taste.UMAMI), List.of("깻잎", "고춧가루")),
                20L, recipe(20L, "깻잎전", Set.of(Taste.SALTY), List.of("깻잎", "고추"))));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), null, null, null, false,
                List.of("깻잎"), null);

        RecipeCandidates result = facade.findCandidates(query);

        assertThat(result.candidates().get(0).recipeId()).isEqualTo(10L);
        assertThat(result.candidates().get(0).reason().matchedWantedTastes()).containsExactly(Taste.SPICY);
        assertThat(result.candidates().get(1).reason().matchedWantedTastes()).isEmpty();
    }

    @Test
    @DisplayName("원하는 맛·피할 맛·회원 선호 맛 어디에도 매운맛이 없으면 재료 이름을 조회하지 않는다")
    void skipsIngredientTexts_whenSpicyDoesNotMatter() throws Exception {
        MyInfoResponse myInfo = mock(MyInfoResponse.class);
        when(myInfo.tastes()).thenReturn(List.of(Taste.SALTY));
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(myInfo);
        givenKeywordRecipes(Map.of(20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기"))));

        facade.findCandidates(new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false, List.of("튀김"), null));

        verify(recipeService, never()).findIngredientTexts(any());
    }

    @Test
    @DisplayName("홍고추만 든 레시피는 매울 수 있음: \"매운 걸로\"에는 원하는 맛과 맞지 않고, \"매운 거 말고\"에는 빠진다")
    void treatsRedChiliOnlyAsMaybeSpicy() throws Exception {
        MyInfoResponse myInfo = mock(MyInfoResponse.class);
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(myInfo);
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "소고기채소불고기", Set.of(Taste.SWEET), List.of("소고기", "고추", "홍고추")),
                20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기")),
                30L, recipe(30L, "닭강정", Set.of(Taste.SWEET), List.of("닭고기")),
                40L, recipe(40L, "꿔바로우", Set.of(Taste.SWEET), List.of("돼지고기"))));
        RecipeQuery wantSpicy = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), null, null, null, false,
                List.of("고기"), null);

        RecipeCandidate bulgogi = facade.findCandidates(wantSpicy).candidates().stream()
                .filter(candidate -> candidate.recipeId() == 10L).findFirst().orElseThrow();
        RecipeCandidates avoiding = facade.findCandidates(avoidSpicy(List.of("고기")));

        assertThat(bulgogi.reason().matchedWantedTastes()).isEmpty();
        assertThat(avoiding.candidates()).extracting(RecipeCandidate::recipeId).doesNotContain(10L);
    }

    @Test
    @DisplayName("고춧가루가 든 레시피는 확실히 매움: \"매운 걸로\"에는 원하는 맛과 맞고, \"매운 거 말고\"에는 빠진다")
    void treatsRedPepperPowderAsDefinitelySpicy() throws Exception {
        MyInfoResponse myInfo = mock(MyInfoResponse.class);
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(myInfo);
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "제육볶음", Set.of(Taste.SWEET), List.of("돼지고기", "고춧가루")),
                20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기")),
                30L, recipe(30L, "닭강정", Set.of(Taste.SWEET), List.of("닭고기")),
                40L, recipe(40L, "꿔바로우", Set.of(Taste.SWEET), List.of("돼지고기"))));
        RecipeQuery wantSpicy = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), null, null, null, false,
                List.of("고기"), null);

        RecipeCandidate first = facade.findCandidates(wantSpicy).candidates().get(0);
        RecipeCandidates avoiding = facade.findCandidates(avoidSpicy(List.of("고기")));

        assertThat(first.recipeId()).isEqualTo(10L);
        assertThat(first.reason().matchedWantedTastes()).containsExactly(Taste.SPICY);
        assertThat(avoiding.candidates()).extracting(RecipeCandidate::recipeId).doesNotContain(10L);
    }

    @Test
    @DisplayName("ES 경로: \"디저트\"처럼 분류만 말하면 냉장고와 겹치지 않는 디저트도 후보에 들어오고 요청 일치로 센다")
    void includesCategoryRecipe_whenNoFridgeOverlap() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "계란", 5)));
        when(candidateSearchService.search(any())).thenReturn(List.of(hit(10L, RecipeCandidateSearchService.MATCH_CATEGORY)));
        when(recipeService.findNonBasicIngredients(Set.of(10L)))
                .thenReturn(List.of(new RecipeCandidateIngredient(10L, 5L, "찹쌀가루", false)));
        when(recipeService.findCandidateInfos(Set.of(10L)))
                .thenReturn(Map.of(10L, new RecipeCandidateInfo(10L, "찹쌀떡", "디저트", 40, 1)));

        RecipeCandidates result = esFacade.findCandidates(
                new RecipeQuery(MEMBER_ID, List.of(), null, List.of("디저트"), null, null, false));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(10L);
        assertThat(result.candidates().get(0).ranking().requestMatches()).isEqualTo(1);
        assertThat(result.candidates().get(0).reason().missingIngredients()).containsExactly(new MissingIngredient(5L, "찹쌀가루"));
        verify(recipeService, never()).findCandidateIngredients(any());
    }

    @Test
    @DisplayName("ES 경로: 분류를 말했으면 요청 조건만 넣은 검색을 한 번 더 해 합치고, 재료·정보는 합친 후보로 한 번씩 읽는다")
    void mergesRequestOnlyHits_whenCategoryGiven() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 5), fridgeItem(2L, "양파", 5)));
        // 전체 조건 검색은 냉장고 재료가 많이 겹치는 레시피만, 요청만 검색(냉장고 재료 없음)은 디저트를 돌려준다
        when(candidateSearchService.search(any())).thenAnswer(invocation -> {
            Condition condition = invocation.getArgument(0);
            return condition.fridgeIngredientIds().isEmpty()
                    ? List.of(hit(30L, RecipeCandidateSearchService.MATCH_CATEGORY))
                    : List.of(hit(20L, RecipeCandidateSearchService.MATCH_FRIDGE), hit(21L, RecipeCandidateSearchService.MATCH_FRIDGE));
        });
        when(recipeService.findNonBasicIngredients(Set.of(20L, 21L, 30L))).thenReturn(List.of(
                new RecipeCandidateIngredient(20L, 1L, "두부", false),
                new RecipeCandidateIngredient(20L, 2L, "양파", false),
                new RecipeCandidateIngredient(21L, 1L, "두부", false),
                new RecipeCandidateIngredient(30L, 5L, "찹쌀가루", false)));
        when(recipeService.findCandidateInfos(Set.of(20L, 21L, 30L))).thenReturn(Map.of(
                20L, new RecipeCandidateInfo(20L, "두부조림", "조림", 20, 2),
                21L, new RecipeCandidateInfo(21L, "두부부침", "전", 20, 1),
                30L, new RecipeCandidateInfo(30L, "찹쌀떡", "디저트", 40, 1)));

        RecipeCandidates result = esFacade.findCandidates(
                new RecipeQuery(MEMBER_ID, List.of(), null, List.of("디저트"), null, null, false));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(20L, 21L, 30L);
        verify(candidateSearchService, times(2)).search(any());
        verify(recipeService, times(1)).findNonBasicIngredients(any());
        verify(recipeService, times(1)).findCandidateInfos(any());
    }

    @Test
    @DisplayName("ES 경로: 분류·키워드를 말하지 않았으면 요청 조건만 넣은 검색은 하지 않는다")
    void skipsRequestOnlySearch_whenNoRequest() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 5)));
        when(candidateSearchService.search(any())).thenReturn(List.of());

        esFacade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of()));

        verify(candidateSearchService, times(1)).search(any());
    }

    @Test
    @DisplayName("ES 경로: 기본 양념인 선택 재료는 검색 조건에서 빼고, 같은 종류 묶음은 펼쳐서 넘긴다")
    void dropsBasicSelectedAndExpandsGroups_whenBuildingCondition() throws Exception {
        Ingredient mushroom = ingredient(45L, "버섯", false);
        Ingredient salt = ingredient(90L, "소금", true);
        when(ingredientService.getIngredientsByIds(List.of(45L, 90L))).thenReturn(Map.of(45L, mushroom, 90L, salt));
        when(ingredientService.findSameKindIdsByNames(any())).thenReturn(Map.of("버섯", List.of(45L, 161L)));
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(candidateSearchService.search(any())).thenReturn(List.of());

        esFacade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of(45L, 90L), true));

        ArgumentCaptor<Condition> condition = ArgumentCaptor.forClass(Condition.class);
        verify(candidateSearchService).search(condition.capture());
        assertThat(condition.getValue().selectedIngredientIds()).containsExactlyInAnyOrder(45L, 161L);
        assertThat(condition.getValue().selectedOnly()).isTrue();
        assertThat(condition.getValue().excludeCategories()).containsExactly("양념", "음료");
    }

    @Test
    @DisplayName("ES 검색이 실패하면 DB 경로로 모아 ES를 쓰지 않을 때와 같은 순서로 정렬한다")
    void fallsBackToDb_whenSearchFails() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 0), fridgeItem(2L, "양파", 5)));
        when(candidateSearchService.search(any())).thenThrow(new IOException("es down"));
        when(recipeService.findCandidateIngredients(any())).thenReturn(List.of(
                new RecipeCandidateIngredient(10L, 2L, "양파", false),
                new RecipeCandidateIngredient(20L, 1L, "두부", false),
                new RecipeCandidateIngredient(20L, 2L, "양파", false)));
        when(recipeService.findCandidateInfos(any())).thenReturn(Map.of(
                10L, new RecipeCandidateInfo(10L, "양파볶음", "볶음", 10, 1),
                20L, new RecipeCandidateInfo(20L, "두부조림", "조림", 20, 2)));

        List<Long> fromFallback = esFacade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of())).candidates().stream()
                .map(RecipeCandidate::recipeId).toList();
        List<Long> fromDb = facade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of())).candidates().stream()
                .map(RecipeCandidate::recipeId).toList();

        assertThat(fromFallback).containsExactly(20L, 10L);
        assertThat(fromFallback).isEqualTo(fromDb);
    }

    @Test
    @DisplayName("ES를 쓰지 않는 설정이면 후보 검색을 부르지 않고 DB 경로로 모은다")
    void fallsBackToDb_whenElasticsearchDisabled() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 5)));

        facade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of()));

        verify(candidateSearchService, never()).search(any());
        verify(recipeService).findCandidateIngredients(any());
    }

    @Test
    @DisplayName("ES 경로: 피할 맛을 빼서 3개가 안 되면 피할 맛 없이 다시 검색해 새로 나온 레시피를 뒤에 채운다")
    void refillsAvoidedRecipes_whenFewerThanThree() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 5)));
        when(candidateSearchService.search(any())).thenAnswer(invocation -> {
            Condition condition = invocation.getArgument(0);
            return condition.avoidTastes().isEmpty()
                    ? List.of(hit(20L, RecipeCandidateSearchService.MATCH_FRIDGE), hit(10L, RecipeCandidateSearchService.MATCH_FRIDGE),
                    hit(30L, RecipeCandidateSearchService.MATCH_FRIDGE))
                    : List.of(hit(20L, RecipeCandidateSearchService.MATCH_FRIDGE));
        });
        givenEsRecipes(Map.of(
                10L, new RecipeCandidateInfo(10L, "마파두부", "볶음", 20, 1),
                20L, new RecipeCandidateInfo(20L, "두부조림", "조림", 20, 1),
                30L, new RecipeCandidateInfo(30L, "두부김치", "볶음", 20, 1)));

        RecipeCandidates result = esFacade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of()).withAvoidTastes(List.of(Taste.SPICY)));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(20L, 10L, 30L);
        verify(candidateSearchService, times(2)).search(any());
        // 다시 검색에서 새로 나온 레시피만 더 읽는다
        verify(recipeService).findNonBasicIngredients(Set.of(20L));
        verify(recipeService).findNonBasicIngredients(Set.of(10L, 30L));
    }

    @Test
    @DisplayName("ES 경로: 피할 맛을 빼도 3개 이상이면 다시 검색하지 않는다")
    void skipsRefillSearch_whenEnoughCandidates() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 5)));
        when(candidateSearchService.search(any())).thenReturn(List.of(
                hit(10L, RecipeCandidateSearchService.MATCH_FRIDGE), hit(20L, RecipeCandidateSearchService.MATCH_FRIDGE),
                hit(30L, RecipeCandidateSearchService.MATCH_FRIDGE)));
        givenEsRecipes(Map.of(
                10L, new RecipeCandidateInfo(10L, "두부부침", "전", 20, 1),
                20L, new RecipeCandidateInfo(20L, "두부조림", "조림", 20, 1),
                30L, new RecipeCandidateInfo(30L, "두부국", "국", 20, 1)));

        RecipeCandidates result = esFacade.findCandidates(RecipeQuery.of(MEMBER_ID, List.of()).withAvoidTastes(List.of(Taste.SPICY)));

        assertThat(result.candidates()).hasSize(3);
        verify(candidateSearchService, times(1)).search(any());
    }

    @Test
    @DisplayName("ES 경로: 키워드로 맞은 레시피가 없으면 레시피 검색(벡터 보충)으로 찾은 레시피를 더하고 벡터로 찾은 것으로 표시한다")
    void addsVectorHits_whenNoKeywordMatch() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(candidateSearchService.search(any())).thenReturn(List.of());
        when(recipeSearchService.search(any(RecipeListRequest.class))).thenReturn(new RecipeSearchResult(List.of(40L), 1, true));
        givenEsRecipes(Map.of(40L, new RecipeCandidateInfo(40L, "탕수육", "튀김", 40, 1)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false, List.of("김피탕"), "김피탕");

        RecipeCandidates result = esFacade.findCandidates(query);

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactly(40L);
        assertThat(result.candidates().get(0).reason().keywordMatch().type()).isEqualTo(KeywordMatch.Type.VECTOR);
        assertThat(result.keywordsNotFound()).isTrue();
        verify(recipeService, times(1)).findNonBasicIngredients(Set.of(40L));
    }

    @Test
    @DisplayName("ES 경로: 키워드로 맞은 레시피가 있으면 벡터 보충을 하지 않고, 이름이 맞은 것은 이름으로 가른다")
    void skipsVectorSearch_whenKeywordMatched() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(candidateSearchService.search(any())).thenReturn(List.of(hit(20L, RecipeCandidateSearchService.MATCH_KEYWORD)));
        givenEsRecipes(Map.of(20L, new RecipeCandidateInfo(20L, "물냉면", "면류", 30, 1)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false, List.of("냉면"), "냉면");

        RecipeCandidates result = esFacade.findCandidates(query);

        assertThat(result.candidates().get(0).reason().keywordMatch().type()).isEqualTo(KeywordMatch.Type.NAME);
        assertThat(result.keywordsNotFound()).isFalse();
        verify(recipeSearchService, never()).search(any());
    }

    @Test
    @DisplayName("ES 경로: \"디저트 추천해줘\"를 두 번 하면 두 번째는 이미 추천한 디저트를 뒤로 보내 다른 디저트가 앞에 온다")
    void ranksOtherDesserts_whenSameRequestAgain() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(candidateSearchService.search(any())).thenReturn(List.of(
                hit(10L, RecipeCandidateSearchService.MATCH_CATEGORY), hit(20L, RecipeCandidateSearchService.MATCH_CATEGORY),
                hit(30L, RecipeCandidateSearchService.MATCH_CATEGORY), hit(40L, RecipeCandidateSearchService.MATCH_CATEGORY),
                hit(50L, RecipeCandidateSearchService.MATCH_CATEGORY), hit(60L, RecipeCandidateSearchService.MATCH_CATEGORY)));
        givenEsRecipes(Map.of(
                10L, new RecipeCandidateInfo(10L, "단호박푸딩", "디저트", 30, 1),
                20L, new RecipeCandidateInfo(20L, "건강 마늘 꿀환", "디저트", 30, 1),
                30L, new RecipeCandidateInfo(30L, "자색고구마찹쌀푸딩", "디저트", 30, 1),
                40L, new RecipeCandidateInfo(40L, "완두콩 롤", "디저트", 30, 1),
                50L, new RecipeCandidateInfo(50L, "오이 소르베", "디저트", 30, 1),
                60L, new RecipeCandidateInfo(60L, "현미 크레이프", "디저트", 30, 1)));
        RecipeQuery dessert = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("디저트"), null, null, false);

        List<RecipeCandidate> first = RecommendationPicker.pick(esFacade.findCandidates(dessert).candidates());
        Set<Long> firstIds = first.stream().map(RecipeCandidate::recipeId).collect(Collectors.toSet());
        List<RecipeCandidate> second = RecommendationPicker.pick(
                esFacade.findCandidates(dessert.withAlreadyRecommended(firstIds)).candidates());

        assertThat(second).extracting(RecipeCandidate::category).containsOnly("디저트");
        assertThat(second).extracting(RecipeCandidate::recipeId).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    @DisplayName("ES 경로: \"탕짜면\"처럼 ES가 글자 일부로만 걸어 온 레시피뿐이면 키워드로 맞은 게 없으므로 벡터로 보충하고 경로 로그에 벡터를 남긴다")
    void suppliesVector_whenEsKeywordHitsAreWeak(CapturedOutput output) throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(candidateSearchService.search(any())).thenReturn(List.of(
                hit(70L, RecipeCandidateSearchService.MATCH_KEYWORD), hit(71L, RecipeCandidateSearchService.MATCH_KEYWORD)));
        when(recipeSearchService.search(any(RecipeListRequest.class))).thenReturn(new RecipeSearchResult(List.of(80L), 1, true));
        givenEsRecipes(Map.of(
                70L, new RecipeCandidateInfo(70L, "간장게장", "절임", 30, 1),
                71L, new RecipeCandidateInfo(71L, "볶음쌀국수", "면류", 30, 1),
                80L, new RecipeCandidateInfo(80L, "유니짜장", "면류", 30, 1)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false, List.of("탕짜면"), null);

        RecipeCandidates result = esFacade.findCandidates(query);

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).contains(80L);
        verify(recipeSearchService, times(1)).search(any());
        assertThat(output).contains("경로: ES+요청만+벡터");
    }

    @Test
    @DisplayName("ES 경로: 글자로 0건인 키워드가 여럿이어도 벡터 검색은 앞의 1개만 한다")
    void callsVectorOnce_whenManyKeywordsHaveNoHits() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(candidateSearchService.search(any())).thenReturn(List.of());
        when(recipeSearchService.search(any(RecipeListRequest.class))).thenReturn(new RecipeSearchResult(List.of(), 0, true));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("탕짜면", "짜장면", "중화요리"), "탕짜면");

        esFacade.findCandidates(query);

        ArgumentCaptor<RecipeListRequest> request = ArgumentCaptor.forClass(RecipeListRequest.class);
        verify(recipeSearchService, times(1)).search(request.capture());
        assertThat(request.getValue().keyword()).isEqualTo("탕짜면");
    }

    @Test
    @DisplayName("ES 경로: 키워드 결과로 표시되지 않은 후보도 이름에 키워드가 있으면 키워드로 맞은 것으로 보고 벡터 보충을 하지 않는다")
    void countsNameMatch_whenEsDidNotMarkKeyword() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        // ES는 키워드가 여럿이면 마지막 키워드(깐풍기)로 맞은 것만 표시한다. 탕수육은 표시 없이 온다
        when(candidateSearchService.search(any())).thenReturn(List.of(
                hit(433L, RecipeCandidateSearchService.MATCH_FRIDGE), hit(366L, RecipeCandidateSearchService.MATCH_KEYWORD)));
        givenEsRecipes(Map.of(
                433L, new RecipeCandidateInfo(433L, "탕수육", "튀김", 40, 1),
                366L, new RecipeCandidateInfo(366L, "깐풍기", "튀김", 40, 1)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("김피탕", "탕수육", "깐풍기"), "김피탕");

        RecipeCandidates result = esFacade.findCandidates(query);

        RecipeCandidate tangsuyuk = result.candidates().stream().filter(c -> c.recipeId() == 433L).findFirst().orElseThrow();
        assertThat(tangsuyuk.reason().keywordMatch().type()).isEqualTo(KeywordMatch.Type.EXPANDED);
        assertThat(tangsuyuk.ranking().requestMatches()).isEqualTo(1);
        verify(recipeSearchService, never()).search(any());
    }

    @Test
    @DisplayName("ES 경로: 벡터 보충 후보도 ES와 같은 조건으로 걸러 양념·음료 분류, 뺄 재료(기본 양념 포함), 시간 초과, 피할 맛이면 카드에 오르지 않는다")
    void filtersVectorSupplement_withSameConditionsAsEs() throws Exception {
        long cucumber = 7L;
        long garlic = 8L;
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(fridgeItem(1L, "두부", 5)));
        // ES는 냉장고로만 맞은 후보 3개(키워드로 맞은 것 없음) → 벡터 보충
        when(candidateSearchService.search(any())).thenReturn(List.of(
                hit(10L, RecipeCandidateSearchService.MATCH_FRIDGE), hit(20L, RecipeCandidateSearchService.MATCH_FRIDGE),
                hit(30L, RecipeCandidateSearchService.MATCH_FRIDGE)));
        when(recipeSearchService.search(any(RecipeListRequest.class)))
                .thenReturn(new RecipeSearchResult(List.of(90L, 91L, 92L, 93L, 94L, 95L), 6, true));
        Map<Long, RecipeCandidateInfo> infos = Map.of(
                10L, new RecipeCandidateInfo(10L, "두부부침", "전", 20, 1),
                20L, new RecipeCandidateInfo(20L, "두부조림", "조림", 20, 1),
                30L, new RecipeCandidateInfo(30L, "두부국", "국", 20, 1),
                90L, new RecipeCandidateInfo(90L, "짜장 소스", "양념", 10, 1),
                91L, new RecipeCandidateInfo(91L, "중국식 냉채", "무침", 20, 1),
                92L, new RecipeCandidateInfo(92L, "짜장 덮밥", "밥", 90, 1),
                93L, new RecipeCandidateInfo(93L, "짬뽕밥", "밥", 20, 1),
                94L, new RecipeCandidateInfo(94L, "마늘 볶음밥", "밥", 20, 1),
                95L, new RecipeCandidateInfo(95L, "중국식 음료", "음료", 5, 1));
        when(recipeService.findCandidateInfos(any())).thenAnswer(invocation -> filter(infos, invocation.getArgument(0)));
        when(recipeService.findNonBasicIngredients(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().filter(infos::containsKey)
                    .map(id -> id == 91L
                            ? new RecipeCandidateIngredient(id, cucumber, "오이", false)
                            : new RecipeCandidateIngredient(id, 1L, "두부", false))
                    .toList();
        });
        // 기본 양념인 뺄 재료(마늘)는 정렬 단계 재료 줄에 없어 따로 조회한다
        when(recipeService.findCandidateIngredients(List.of(cucumber, garlic))).thenReturn(List.of(
                new RecipeCandidateIngredient(91L, cucumber, "오이", false),
                new RecipeCandidateIngredient(94L, garlic, "마늘", true)));
        when(recipeService.findTastesByRecipeIds(any())).thenAnswer(invocation ->
                filter(Map.of(93L, Set.of(Taste.SPICY)), invocation.getArgument(0)));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, 30, List.of(cucumber, garlic), false,
                List.of("탕짜면"), null).withAvoidTastes(List.of(Taste.SPICY));

        RecipeCandidates result = esFacade.findCandidates(query);

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    // ES로 찾히는 레시피들. 모두 두부(1번)를 쓰고, 정보·재료는 요청한 ID만 돌려준다
    private void givenEsRecipes(Map<Long, RecipeCandidateInfo> infos) {
        when(recipeService.findCandidateInfos(any())).thenAnswer(invocation -> filter(infos, invocation.getArgument(0)));
        when(recipeService.findNonBasicIngredients(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().filter(infos::containsKey)
                    .map(id -> new RecipeCandidateIngredient(id, 1L, "두부", false))
                    .toList();
        });
    }

    private static CandidateHit hit(Long recipeId, String... matched) {
        return new CandidateHit(recipeId, 1.0, Set.of(matched));
    }

    private static Ingredient ingredient(Long id, String name, boolean basicSeasoning) {
        Ingredient ingredient = Ingredient.builder().ingredientCode("ING_" + id).name(name).baseUnit("g")
                .basicSeasoning(basicSeasoning).build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private RecipeQuery avoidSpicy(List<String> keywords) {
        return new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                keywords == null ? List.of("튀김") : keywords, null).withAvoidTastes(List.of(Taste.SPICY));
    }

    private record TestRecipe(RecipeCandidateInfo info, Set<Taste> tastes, List<String> ingredientTexts) {
    }

    private static TestRecipe recipe(Long id, String name, Set<Taste> tastes, List<String> ingredientTexts) {
        return new TestRecipe(new RecipeCandidateInfo(id, name, "튀김", 30, 1), tastes, ingredientTexts);
    }

    // 키워드 검색으로 찾히는 레시피들 (맛 데이터·재료 이름까지)
    private void givenKeywordRecipes(Map<Long, TestRecipe> recipes) throws Exception {
        List<Long> ids = recipes.keySet().stream().sorted().toList();
        when(recipeSearchService.search(any(RecipeListRequest.class))).thenReturn(new RecipeSearchResult(ids, ids.size()));
        Map<Long, RecipeCandidateInfo> infos = new HashMap<>();
        Map<Long, Set<Taste>> tastes = new HashMap<>();
        Map<Long, List<String>> texts = new HashMap<>();
        recipes.forEach((id, recipe) -> {
            infos.put(id, recipe.info());
            tastes.put(id, recipe.tastes());
            texts.put(id, recipe.ingredientTexts());
        });
        when(recipeService.findCandidateInfos(any())).thenAnswer(invocation -> filter(infos, invocation.getArgument(0)));
        when(recipeService.findTastesByRecipeIds(any())).thenAnswer(invocation -> filter(tastes, invocation.getArgument(0)));
        when(recipeService.findIngredientTexts(any())).thenAnswer(invocation -> filter(texts, invocation.getArgument(0)));
    }

    private static <T> Map<Long, T> filter(Map<Long, T> values, Collection<Long> ids) {
        Map<Long, T> result = new HashMap<>();
        ids.forEach(id -> {
            if (values.containsKey(id)) {
                result.put(id, values.get(id));
            }
        });
        return result;
    }

    private static FridgeItemResponse fridgeItem(long ingredientId, String name, long dDay) {
        FridgeItemStatus status = FridgeItemStatus.of(dDay);
        return new FridgeItemResponse(
                ingredientId * 10, ingredientId, name, "개", name, BigDecimal.ONE, StorageType.REFRIGERATED,
                LocalDate.now().plusDays(dDay), FridgeSource.MANUAL, null,
                status == FridgeItemStatus.IMMINENT, status == FridgeItemStatus.EXPIRED, dDay, status,
                IngredientType.NORMAL, false, LocalDateTime.now(), LocalDateTime.now());
    }
}

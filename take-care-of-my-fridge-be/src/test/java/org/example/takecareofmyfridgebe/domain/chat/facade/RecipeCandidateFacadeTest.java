package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
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
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeCandidateFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private MemberService memberService;
    private FridgeService fridgeService;
    private RecipeService recipeService;
    private IngredientService ingredientService;
    private RecipeSearchService recipeSearchService;
    private RecipeCandidateFacade facade;

    @BeforeEach
    void setUp() {
        fridgeService = mock(FridgeService.class);
        memberService = mock(MemberService.class);
        recipeService = mock(RecipeService.class);
        ingredientService = mock(IngredientService.class);
        recipeSearchService = mock(RecipeSearchService.class);
        facade = new RecipeCandidateFacade(fridgeService, memberService, recipeService, ingredientService, recipeSearchService,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of("청양고추", "홍고추", "청고추", "고춧가루", "고추장", "건고추", "베트남고추", "페퍼론치노", "핫소스", "고추기름")));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of());
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
    @DisplayName("\"매운 거 말고\"면 맛 데이터나 매운 재료(원문 홍고추·청고추)로 매운 레시피를 후보에서 뺀다")
    void dropsSpicyRecipes_whenAvoidingSpicy() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        givenKeywordRecipes(Map.of(
                10L, recipe(10L, "깐풍기", Set.of(Taste.SALTY, Taste.SWEET), List.of("닭고기", "고추", "홍고추")),
                20L, recipe(20L, "탕수육", Set.of(Taste.SWEET), List.of("돼지고기")),
                30L, recipe(30L, "장어조림", Set.of(Taste.SALTY), List.of("장어", "고추", "청고추")),
                40L, recipe(40L, "닭강정", Set.of(Taste.SWEET), List.of("닭고기")),
                50L, recipe(50L, "유린기", Set.of(Taste.SPICY), List.of("닭고기")),
                60L, recipe(60L, "꿔바로우", Set.of(Taste.SWEET), List.of("돼지고기", "고추"))));

        RecipeCandidates result = facade.findCandidates(avoidSpicy(List.of("튀김")));

        assertThat(result.candidates()).extracting(RecipeCandidate::recipeId).containsExactlyInAnyOrder(20L, 40L, 60L);
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

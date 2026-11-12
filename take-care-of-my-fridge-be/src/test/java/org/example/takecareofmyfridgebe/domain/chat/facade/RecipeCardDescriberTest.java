package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSpicyLevel;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeTasteLevel;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateFacadeTest.CANDIDATE_PROPERTIES;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateFacadeTest.fridgeItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeCardDescriberTest {

    private static final Long MEMBER_ID = 1L;

    private MemberService memberService;
    private FridgeService fridgeService;
    private RecipeService recipeService;
    private RecipeCandidateSearchService candidateSearchService;
    // ES를 쓰지 않는 설정 (맛·매운 정도를 DB로 계산)
    private RecipeCardDescriber facade;
    // ES를 쓰는 설정 (맛·매운 정도를 ES에서 id로 읽음)
    private RecipeCardDescriber esFacade;

    @BeforeEach
    void setUp() {
        fridgeService = mock(FridgeService.class);
        memberService = mock(MemberService.class);
        recipeService = mock(RecipeService.class);
        candidateSearchService = mock(RecipeCandidateSearchService.class);
        facade = describerWith(SearchEngine.MYSQL);
        esFacade = describerWith(SearchEngine.ELASTICSEARCH);
        when(fridgeService.getItems(any(), any())).thenReturn(List.of());
    }

    // 맛 읽기(TasteReader)는 같은 목으로 만든 실제 객체를 넣는다
    private RecipeCardDescriber describerWith(SearchEngine engine) {
        RecipeTasteReader tasteReader = new RecipeTasteReader(recipeService, candidateSearchService,
                new RecipeSearchProperties(engine, null, null), CANDIDATE_PROPERTIES);
        return new RecipeCardDescriber(fridgeService, memberService, recipeService, tasteReader, CANDIDATE_PROPERTIES);
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
    @DisplayName("밀키트 추천 재료는 냉장고를 한 번 읽어 기한이 남은 냉장고 밀키트(상품 ID, 없으면 등록 이름)와 연결 레시피 계산·맛을 돌려주고, 레시피가 없으면 레시피를 조회하지 않는다")
    void describesMealkitRecipes_withFridgeMealkitNames() {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of(
                fridgeItem(1L, "두부", 5),
                mealkitItem("황태해장국", 2),
                mealkitItem("된장국 밀키트", 3, 24L),
                mealkitItem("된장국 밀키트", 1, 24L),
                mealkitItem("지난 된장국", -1)));
        when(recipeService.findNonBasicIngredients(List.of(543L))).thenReturn(List.of(
                new RecipeCandidateIngredient(543L, 1L, "두부", false),
                new RecipeCandidateIngredient(543L, 4L, "황태", false)));
        when(recipeService.findCandidateInfos(List.of(543L))).thenReturn(Map.of(
                543L, new RecipeCandidateInfo(543L, "황태해장국", "국", 20, 2)));
        when(recipeService.findTastesByRecipeIds(List.of(543L))).thenReturn(Map.of(543L, Set.of(Taste.UMAMI)));

        RecipeCardDescriber.MealkitRecipes found = facade.describeMealkitRecipes(MEMBER_ID, List.of(543L));
        RecipeCardDescriber.MealkitRecipes noRecipe = facade.describeMealkitRecipes(MEMBER_ID, List.of());

        assertThat(found.fridgeMealkits().dDayByName()).containsExactly(Map.entry("황태해장국", 2L));
        assertThat(found.fridgeMealkits().dDayByProductId()).containsExactly(Map.entry(24L, 1L));
        assertThat(found.recipes().get(543L).ranking().ownedCount()).isEqualTo(1);
        assertThat(found.recipeTastes()).containsEntry(543L, Set.of(Taste.UMAMI));
        assertThat(noRecipe.recipes()).isEmpty();
        assertThat(noRecipe.fridgeMealkits().dDayByName()).containsOnlyKeys("황태해장국");
        verify(recipeService, times(1)).findCandidateInfos(any());
        verify(fridgeService, times(2)).getItems(any(), any());
    }

    @Test
    @DisplayName("밀키트 연결 레시피의 맛·매운 정도는 ES에서 id로 한 번에 읽어, 맛 데이터에 매운맛이 없어도 MILD·HOT이면 매울 수 있는 레시피로 돌려주고 DB 맛·재료 이름은 읽지 않는다")
    void readsMealkitRecipeLevels_fromEsByIds() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeService.findCandidateInfos(List.of(539L, 59L, 544L))).thenReturn(Map.of(
                539L, new RecipeCandidateInfo(539L, "부추 콩가루 찜", "찜", 20, 2),
                59L, new RecipeCandidateInfo(59L, "고추잡채", "볶음", 20, 2),
                544L, new RecipeCandidateInfo(544L, "된장국", "국", 20, 2)));
        when(candidateSearchService.findTasteLevels(List.of(539L, 59L, 544L))).thenReturn(Map.of(
                539L, new RecipeTasteLevel(Set.of(Taste.MILD, Taste.SPICY), RecipeSpicyLevel.HOT),
                59L, new RecipeTasteLevel(Set.of(Taste.SALTY), RecipeSpicyLevel.MILD),
                544L, new RecipeTasteLevel(Set.of(Taste.UMAMI), RecipeSpicyLevel.NONE)));

        RecipeCardDescriber.MealkitRecipes found = esFacade.describeMealkitRecipes(MEMBER_ID, List.of(539L, 59L, 544L));

        assertThat(found.maybeSpicyRecipeIds()).containsExactlyInAnyOrder(539L, 59L);
        assertThat(found.recipeTastes()).containsEntry(539L, Set.of(Taste.MILD, Taste.SPICY))
                .containsEntry(59L, Set.of(Taste.SALTY));
        verify(recipeService, never()).findTastesByRecipeIds(any());
        verify(recipeService, never()).findIngredientTexts(any());
    }

    @Test
    @DisplayName("밀키트 연결 레시피의 ES 조회가 실패하면 DB 맛 데이터·재료 이름으로 매운 정도를 계산한다 (정확한 이름 고추는 매울 수 있음, 고춧가루는 확실히 매움)")
    void computesMealkitRecipeLevelsFromDb_whenEsLookupFails() throws Exception {
        when(memberService.getMyInfo(MEMBER_ID)).thenReturn(mock(MyInfoResponse.class));
        when(recipeService.findCandidateInfos(List.of(539L, 59L))).thenReturn(Map.of(
                539L, new RecipeCandidateInfo(539L, "부추 콩가루 찜", "찜", 20, 2),
                59L, new RecipeCandidateInfo(59L, "고추잡채", "볶음", 20, 2)));
        when(candidateSearchService.findTasteLevels(any())).thenThrow(new IOException("es down"));
        when(recipeService.findTastesByRecipeIds(List.of(539L, 59L)))
                .thenReturn(Map.of(539L, Set.of(Taste.MILD), 59L, Set.of(Taste.SALTY)));
        when(recipeService.findIngredientTexts(List.of(539L, 59L)))
                .thenReturn(Map.of(539L, List.of("부추", "고춧가루"), 59L, List.of("돼지고기", "고추")));

        RecipeCardDescriber.MealkitRecipes found = esFacade.describeMealkitRecipes(MEMBER_ID, List.of(539L, 59L));

        assertThat(found.maybeSpicyRecipeIds()).containsExactlyInAnyOrder(539L, 59L);
        assertThat(found.recipeTastes().get(539L)).contains(Taste.SPICY);
        assertThat(found.recipeTastes().get(59L)).doesNotContain(Taste.SPICY);
    }

    private static FridgeItemResponse mealkitItem(String rawName, long dDay) {
        return mealkitItem(rawName, dDay, null);
    }

    private static FridgeItemResponse mealkitItem(String rawName, long dDay, Long productId) {
        FridgeItemStatus status = FridgeItemStatus.of(dDay);
        return new FridgeItemResponse(
                900L + dDay, 99L, "밀키트", "개", rawName, BigDecimal.ONE, StorageType.REFRIGERATED,
                LocalDate.now().plusDays(dDay), FridgeSource.COMMERCE, 1L,
                status == FridgeItemStatus.IMMINENT, status == FridgeItemStatus.EXPIRED, dDay, status,
                IngredientType.MEALKIT, false, LocalDateTime.now(), LocalDateTime.now(), productId);
    }
}

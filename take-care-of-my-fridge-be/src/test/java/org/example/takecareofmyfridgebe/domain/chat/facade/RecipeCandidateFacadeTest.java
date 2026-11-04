package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeCandidateFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private MemberService memberService;
    private RecipeService recipeService;
    private IngredientService ingredientService;
    private RecipeSearchService recipeSearchService;
    private RecipeCandidateFacade facade;

    @BeforeEach
    void setUp() {
        FridgeService fridgeService = mock(FridgeService.class);
        memberService = mock(MemberService.class);
        recipeService = mock(RecipeService.class);
        ingredientService = mock(IngredientService.class);
        recipeSearchService = mock(RecipeSearchService.class);
        facade = new RecipeCandidateFacade(fridgeService, memberService, recipeService, ingredientService, recipeSearchService,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4));
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
    @DisplayName("키워드마다 레시피 검색으로 레시피를 찾아 그 레시피의 재료까지 받아 후보로 넣는다")
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
}

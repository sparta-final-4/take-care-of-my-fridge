package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    private RecipeCandidateFacade facade;

    @BeforeEach
    void setUp() {
        FridgeService fridgeService = mock(FridgeService.class);
        memberService = mock(MemberService.class);
        recipeService = mock(RecipeService.class);
        ingredientService = mock(IngredientService.class);
        facade = new RecipeCandidateFacade(fridgeService, memberService, recipeService, ingredientService,
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
}

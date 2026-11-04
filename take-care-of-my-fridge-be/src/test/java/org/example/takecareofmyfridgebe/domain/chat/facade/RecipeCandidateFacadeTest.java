package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeCandidateFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private MemberService memberService;
    private RecipeCandidateFacade facade;

    @BeforeEach
    void setUp() {
        FridgeService fridgeService = mock(FridgeService.class);
        RecipeService recipeService = mock(RecipeService.class);
        memberService = mock(MemberService.class);
        facade = new RecipeCandidateFacade(fridgeService, memberService, recipeService,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4));
        when(fridgeService.getItems(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("원하는 맛을 말했으면 회원 선호 맛을 조회하지 않는다")
    void skipsMemberTastes_whenWantedTastesExist() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), null, null, null, null, false);

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
}

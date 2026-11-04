package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionStatusCounts;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse.RecommendedRecipeName;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionQueryFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private ChatSessionService chatSessionService;
    private ChatMessageService chatMessageService;
    private RecipeService recipeService;
    private ChatSessionQueryFacade facade;
    private final ChatSessionListRequest request = new ChatSessionListRequest(1, 10, null);

    @BeforeEach
    void setUp() {
        chatSessionService = mock(ChatSessionService.class);
        chatMessageService = mock(ChatMessageService.class);
        recipeService = mock(RecipeService.class);
        facade = new ChatSessionQueryFacade(chatSessionService, chatMessageService, recipeService);
    }

    @Test
    @DisplayName("상담마다 최근 추천 메뉴 이름을 붙이고, 지워진 레시피는 빼며, 추천·이름 조회는 한 번씩이다")
    void attachesRecommendedRecipeNames() {
        when(chatSessionService.getMySessions(MEMBER_ID, request)).thenReturn(listOf(summary(1L), summary(2L)));
        when(chatMessageService.findRecentRecommendedRecipeIds(List.of(1L, 2L), 3))
                .thenReturn(Map.of(1L, List.of(10L, 99L, 20L)));
        when(recipeService.findSummaries(any())).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "두부조림", null, null), 20L, new RecipeSummary(20L, "탕수육", null, null)));

        ChatSessionListResponse response = facade.getMySessions(MEMBER_ID, request);

        assertThat(response.sessions().content().get(0).recommendedRecipes()).containsExactly(
                new RecommendedRecipeName(10L, "두부조림"), new RecommendedRecipeName(20L, "탕수육"));
        assertThat(response.sessions().content().get(1).recommendedRecipes()).isEmpty();
        assertThat(response.sessions().totalElements()).isEqualTo(2);
        verify(recipeService, times(1)).findSummaries(any());
    }

    @Test
    @DisplayName("추천이 없으면 레시피 이름을 조회하지 않는다")
    void skipsRecipeLookup_whenNoRecommendations() {
        when(chatSessionService.getMySessions(MEMBER_ID, request)).thenReturn(listOf(summary(1L)));
        when(chatMessageService.findRecentRecommendedRecipeIds(List.of(1L), 3)).thenReturn(Map.of());

        assertThat(facade.getMySessions(MEMBER_ID, request).sessions().content().get(0).recommendedRecipes()).isEmpty();
        verify(recipeService, never()).findSummaries(any());
    }

    private static ChatSessionListResponse listOf(ChatSessionSummaryResponse... sessions) {
        return new ChatSessionListResponse(new ChatSessionStatusCounts(sessions.length, sessions.length, 0),
                new PageResponse<>(List.of(sessions), 1, 10, sessions.length, 1, true));
    }

    private static ChatSessionSummaryResponse summary(Long sessionId) {
        return new ChatSessionSummaryResponse(sessionId, ChatSessionStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(),
                2, "질문", "답", List.of());
    }
}

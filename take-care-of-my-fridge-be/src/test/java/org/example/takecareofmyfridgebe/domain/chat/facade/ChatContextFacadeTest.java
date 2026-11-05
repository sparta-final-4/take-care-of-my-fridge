package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatContextStore;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatContextFacadeTest {

    private static final Long SESSION_ID = 10L;

    private ChatMessageService chatMessageService;
    private RecipeService recipeService;
    private ChatContextStore chatContextStore;
    private ChatContextFacade facade;

    @BeforeEach
    void setUp() {
        chatMessageService = mock(ChatMessageService.class);
        recipeService = mock(RecipeService.class);
        chatContextStore = mock(ChatContextStore.class);
        facade = new ChatContextFacade(chatMessageService, recipeService, chatContextStore);
    }

    @Test
    @DisplayName("최근 메시지·카드·카드 이름을 한 번씩 읽어 오래된 메시지부터 카드 순서대로 이름을 붙이고, 지워진 레시피는 뺀다")
    void loadsRecentMessagesWithCardNames_withOneQueryEach() {
        when(chatMessageService.findRecentMessages(SESSION_ID)).thenReturn(List.of(
                message(1L, ChatMessageRole.USER, "김피탕 같은 요리"),
                message(2L, ChatMessageRole.ASSISTANT, "비슷한 걸 골랐어요."),
                message(3L, ChatMessageRole.USER, "고마워"),
                message(4L, ChatMessageRole.ASSISTANT, "맛있게 드세요.")));
        when(chatMessageService.findRecipeRecommendations(List.of(2L, 4L))).thenReturn(List.of(
                new SavedRecommendation(2L, 20L, 1, null),
                new SavedRecommendation(2L, 10L, 0, null),
                new SavedRecommendation(2L, 99L, 2, null)));
        when(recipeService.findSummaries(any())).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "탕수육", null, null), 20L, new RecipeSummary(20L, "깐풍기", null, null)));

        ChatContext context = facade.load(SESSION_ID);

        assertThat(context.recentMessages()).extracting(ContextMessage::content)
                .containsExactly("김피탕 같은 요리", "비슷한 걸 골랐어요.", "고마워", "맛있게 드세요.");
        assertThat(context.recentMessages().get(1).cards())
                .containsExactly(new CardName(10L, "탕수육"), new CardName(20L, "깐풍기"));
        assertThat(context.recentMessages().get(3).cards()).isEmpty();
        verify(chatMessageService, times(1)).findRecentMessages(SESSION_ID);
        verify(chatMessageService, times(1)).findRecipeRecommendations(any());
        verify(recipeService, times(1)).findSummaries(any());
    }

    @Test
    @DisplayName("카드가 없으면 레시피 이름을 읽지 않고, 메시지가 없으면 빈 문맥이다")
    void skipsNameLookup_whenNoCards() {
        when(chatMessageService.findRecentMessages(SESSION_ID)).thenReturn(List.of(
                message(1L, ChatMessageRole.USER, "안녕"), message(2L, ChatMessageRole.ASSISTANT, "안녕하세요")));
        when(chatMessageService.findRecipeRecommendations(List.of(2L))).thenReturn(List.of());

        assertThat(facade.load(SESSION_ID).recentMessages()).hasSize(2);
        verify(recipeService, never()).findSummaries(any());

        when(chatMessageService.findRecentMessages(SESSION_ID)).thenReturn(List.of());
        assertThat(facade.load(SESSION_ID)).isEqualTo(ChatContext.empty());
    }

    @Test
    @DisplayName("직전 추천이 Redis에 있으면 그 조건과 카드 순서대로 이름을 두고, 이름은 최근 대화 카드와 한 번에 읽는다")
    void loadsLastRecommendationFromStore() {
        LastRecommendation last = LastRecommendation.of(RecipeQuery.of(1L, List.of()), List.of(30L, 10L));
        when(chatContextStore.find(SESSION_ID)).thenReturn(Optional.of(last));
        when(chatMessageService.findRecentMessages(SESSION_ID)).thenReturn(List.of(
                message(1L, ChatMessageRole.USER, "추천해줘"), message(2L, ChatMessageRole.ASSISTANT, "골랐어요.")));
        when(chatMessageService.findRecipeRecommendations(List.of(2L))).thenReturn(List.of(new SavedRecommendation(2L, 10L, 0, null)));
        when(recipeService.findSummaries(List.of(10L, 30L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "탕수육", null, null), 30L, new RecipeSummary(30L, "깐풍기", null, null)));

        ChatContext context = facade.load(SESSION_ID);

        assertThat(context.lastRecommendation()).isEqualTo(last);
        assertThat(context.lastCards()).containsExactly(new CardName(30L, "깐풍기"), new CardName(10L, "탕수육"));
        verify(recipeService, times(1)).findSummaries(any());
    }

    @Test
    @DisplayName("직전 추천이 Redis에 없으면 조건 없이, 최근 대화에서 카드가 붙은 마지막 AI 답의 카드를 직전 카드로 쓴다")
    void usesLastCardsInRecent_whenStoreIsEmpty() {
        when(chatContextStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(chatMessageService.findRecentMessages(SESSION_ID)).thenReturn(List.of(
                message(1L, ChatMessageRole.USER, "추천해줘"), message(2L, ChatMessageRole.ASSISTANT, "골랐어요."),
                message(3L, ChatMessageRole.USER, "고마워"), message(4L, ChatMessageRole.ASSISTANT, "맛있게 드세요.")));
        when(chatMessageService.findRecipeRecommendations(List.of(2L, 4L))).thenReturn(List.of(
                new SavedRecommendation(2L, 10L, 0, null), new SavedRecommendation(2L, 20L, 1, null)));
        when(recipeService.findSummaries(any())).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "탕수육", null, null), 20L, new RecipeSummary(20L, "깐풍기", null, null)));

        ChatContext context = facade.load(SESSION_ID);

        assertThat(context.lastRecommendation()).isNull();
        assertThat(context.lastCards()).containsExactly(new CardName(10L, "탕수육"), new CardName(20L, "깐풍기"));
    }

    @Test
    @DisplayName("문맥을 읽다 실패하면 에러로 끝내지 않고 빈 문맥으로 계속한다")
    void returnsEmpty_whenLoadingFails() {
        when(chatMessageService.findRecentMessages(SESSION_ID)).thenThrow(new IllegalStateException("DB 끊김"));

        assertThat(facade.load(SESSION_ID)).isEqualTo(ChatContext.empty());
    }

    @Test
    @DisplayName("AI 답은 300자까지 자르고 보여 준 카드 이름을 번호와 함께 붙여 넘기고, 사용자 메시지는 그대로 넘긴다")
    void writesPromptText() {
        ContextMessage assistant = new ContextMessage(ChatMessageRole.ASSISTANT, "가".repeat(310),
                List.of(new CardName(10L, "탕수육"), new CardName(20L, "깐풍기")));

        assertThat(assistant.promptText()).isEqualTo("가".repeat(300) + "…\n(보여 준 카드: 1. 탕수육 2. 깐풍기)");
        assertThat(new ContextMessage(ChatMessageRole.USER, "두 번째 거", null).promptText()).isEqualTo("두 번째 거");
    }

    private static ChatMessageResponse message(Long id, ChatMessageRole role, String content) {
        return new ChatMessageResponse(id, id.intValue(), role, content, LocalDateTime.now());
    }
}

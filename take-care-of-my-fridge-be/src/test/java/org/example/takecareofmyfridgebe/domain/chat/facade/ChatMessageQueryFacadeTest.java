package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ProductCard;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
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

class ChatMessageQueryFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    private ChatMessageService chatMessageService;
    private ChatRecommendationFacade chatRecommendationFacade;
    private ChatFridgeShopFacade chatFridgeShopFacade;
    private ChatMessageQueryFacade facade;

    @BeforeEach
    void setUp() {
        chatMessageService = mock(ChatMessageService.class);
        chatRecommendationFacade = mock(ChatRecommendationFacade.class);
        chatFridgeShopFacade = mock(ChatFridgeShopFacade.class);
        facade = new ChatMessageQueryFacade(chatMessageService, chatRecommendationFacade, chatFridgeShopFacade);
    }

    @Test
    @DisplayName("AI 메시지 id 묶음으로 추천을 한 번에 읽고, 카드를 한 번에 다시 만들어 각 AI 메시지에 붙인다")
    void attachesCardsToAssistantMessages() {
        when(chatMessageService.getMessages(MEMBER_ID, SESSION_ID)).thenReturn(List.of(
                message(1L, ChatMessageRole.USER), message(2L, ChatMessageRole.ASSISTANT),
                message(3L, ChatMessageRole.USER), message(4L, ChatMessageRole.ASSISTANT)));
        List<SavedRecommendation> saved = List.of(
                new SavedRecommendation(2L, 30L, 0, "설명"), new SavedRecommendation(4L, 40L, 0, null));
        when(chatMessageService.findRecommendations(List.of(2L, 4L))).thenReturn(saved);
        when(chatRecommendationFacade.rebuildCards(MEMBER_ID, saved)).thenReturn(Map.of(
                2L, List.of(card(30L, "설명")), 4L, List.of(card(40L, null))));

        List<ChatMessageResponse> messages = facade.getMessages(MEMBER_ID, SESSION_ID);

        assertThat(messages.get(0).recommendations()).isEmpty();
        assertThat(messages.get(1).recommendations()).extracting(RecommendedRecipe::recipeId).containsExactly(30L);
        assertThat(messages.get(1).recommendations().get(0).description()).isEqualTo("설명");
        assertThat(messages.get(3).recommendations()).extracting(RecommendedRecipe::recipeId).containsExactly(40L);
        verify(chatMessageService, times(1)).findRecommendations(any());
        verify(chatRecommendationFacade, times(1)).rebuildCards(any(), any());
    }

    @Test
    @DisplayName("저장된 추천이 없으면 카드를 다시 만들지 않는다")
    void skipsRebuild_whenNoRecommendations() {
        when(chatMessageService.getMessages(MEMBER_ID, SESSION_ID)).thenReturn(List.of(message(2L, ChatMessageRole.ASSISTANT)));
        when(chatMessageService.findRecommendations(List.of(2L))).thenReturn(List.of());

        assertThat(facade.getMessages(MEMBER_ID, SESSION_ID).get(0).recommendations()).isEmpty();
        verify(chatRecommendationFacade, never()).rebuildCards(any(), any());
    }

    @Test
    @DisplayName("밀키트만 있는 답도 다시 만든 밀키트 카드를 그 AI 메시지에 붙인다")
    void attachesMealkitCards_whenAnswerHasOnlyMealkits() {
        when(chatMessageService.getMessages(MEMBER_ID, SESSION_ID)).thenReturn(List.of(message(2L, ChatMessageRole.ASSISTANT)));
        List<SavedRecommendation> saved = List.of(new SavedRecommendation(2L, null, 41L, 0, null));
        when(chatMessageService.findRecommendations(List.of(2L))).thenReturn(saved);
        when(chatRecommendationFacade.rebuildCards(MEMBER_ID, saved)).thenReturn(Map.of());
        RecommendedMealkit mealkit = new RecommendedMealkit(
                new ShoppingProduct(41L, "황태해장국", null, 9000, null, null, false, null, null), 2, 543L, "황태해장국", null);
        when(chatRecommendationFacade.rebuildMealkitCards(MEMBER_ID, saved)).thenReturn(Map.of(2L, List.of(mealkit)));

        ChatMessageResponse answer = facade.getMessages(MEMBER_ID, SESSION_ID).get(0);

        assertThat(answer.recommendations()).isEmpty();
        assertThat(answer.mealkits()).containsExactly(mealkit);
    }

    @Test
    @DisplayName("사기 답의 상품 카드도 다시 만들어 그 AI 메시지에 붙인다")
    void attachesProductCards() {
        when(chatMessageService.getMessages(MEMBER_ID, SESSION_ID)).thenReturn(List.of(message(2L, ChatMessageRole.ASSISTANT)));
        List<SavedRecommendation> saved = List.of(new SavedRecommendation(2L, null, 7L, 0, "당근"));
        when(chatMessageService.findRecommendations(List.of(2L))).thenReturn(saved);
        ProductCard product = new ProductCard("당근", new ShoppingProduct(7L, "흙당근 1kg", null, 3000, null, null, false, null, null));
        when(chatFridgeShopFacade.rebuildProductCards(saved)).thenReturn(Map.of(2L, List.of(product)));

        ChatMessageResponse answer = facade.getMessages(MEMBER_ID, SESSION_ID).get(0);

        assertThat(answer.products()).containsExactly(product);
        assertThat(answer.mealkits()).isEmpty();
    }

    private static ChatMessageResponse message(Long id, ChatMessageRole role) {
        return new ChatMessageResponse(id, id.intValue(), role, "내용", LocalDateTime.now());
    }

    private static RecommendedRecipe card(Long recipeId, String description) {
        return new RecommendedRecipe(recipeId, "레시피", null, "국", 20, List.of(), List.of(), List.of(), List.of(),
                List.of(), false, "이유", description);
    }
}

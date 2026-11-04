package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatContextStore;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 이번 메시지를 처리하기 전에 상담 문맥(최근 대화와 카드 이름, 직전 추천)을 읽는다.
 * 최근 메시지 1번, 그 메시지들의 카드 1번, 카드 레시피 이름 1번(직전 추천 카드 이름 포함), Redis 1번으로 끝난다.
 * 직전 추천이 Redis에 없으면(만료·장애) 최근 대화에서 카드가 붙은 마지막 AI 답의 카드를 직전 카드로 쓴다 (조건은 없음).
 * 문맥을 못 읽어도 답은 지금처럼 이번 메시지만으로 할 수 있으므로, 실패하면 빈 문맥으로 계속한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextFacade {

    private final ChatMessageService chatMessageService;
    private final RecipeService recipeService;
    private final ChatContextStore chatContextStore;

    public ChatContext load(Long sessionId) {
        try {
            return read(sessionId);
        } catch (RuntimeException e) {
            log.warn("상담 문맥 읽기 실패, 이전 대화 없이 계속 - sessionId: {}, 원인: {}", sessionId, e.toString());
            return ChatContext.empty();
        }
    }

    private ChatContext read(Long sessionId) {
        LastRecommendation last = chatContextStore.find(sessionId).orElse(null);
        List<ChatMessageResponse> messages = chatMessageService.findRecentMessages(sessionId);
        List<Long> assistantIds = messages.stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .map(ChatMessageResponse::id)
                .toList();
        Map<Long, List<SavedRecommendation>> cardsByMessage = assistantIds.isEmpty()
                ? Map.of()
                : chatMessageService.findRecipeRecommendations(assistantIds).stream()
                .collect(Collectors.groupingBy(SavedRecommendation::messageId));
        List<Long> lastIds = last == null ? List.of() : last.recipeIds();
        List<Long> recipeIds = Stream.concat(
                        cardsByMessage.values().stream().flatMap(List::stream).map(SavedRecommendation::recipeId),
                        lastIds.stream())
                .distinct()
                .toList();
        Map<Long, RecipeSummary> summaries = recipeIds.isEmpty() ? Map.of() : recipeService.findSummaries(recipeIds);

        List<ContextMessage> recent = toMessages(messages, cardsByMessage, summaries);
        List<CardName> lastCards = last != null ? names(lastIds, summaries) : lastCardsInRecent(recent);
        return new ChatContext(recent, last, lastCards);
    }

    // 직전 추천이 Redis에 없을 때: 최근 대화에서 카드가 붙은 마지막 AI 답의 카드
    private static List<CardName> lastCardsInRecent(List<ContextMessage> recent) {
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (!recent.get(i).cards().isEmpty()) {
                return recent.get(i).cards();
            }
        }
        return List.of();
    }

    // 레시피 ID 순서대로 이름 (지워진 레시피는 뺀다)
    private static List<CardName> names(List<Long> recipeIds, Map<Long, RecipeSummary> summaries) {
        return recipeIds.stream()
                .map(summaries::get)
                .filter(Objects::nonNull)
                .map(summary -> new CardName(summary.recipeId(), summary.name()))
                .toList();
    }

    private static List<ContextMessage> toMessages(List<ChatMessageResponse> messages,
                                                   Map<Long, List<SavedRecommendation>> cardsByMessage,
                                                   Map<Long, RecipeSummary> summaries) {
        List<ContextMessage> result = new ArrayList<>();
        for (ChatMessageResponse message : messages) {
            List<Long> cardIds = cardsByMessage.getOrDefault(message.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(SavedRecommendation::displayOrder))
                    .map(SavedRecommendation::recipeId)
                    .toList();
            result.add(new ContextMessage(message.role(), message.content(), names(cardIds, summaries)));
        }
        return result;
    }
}

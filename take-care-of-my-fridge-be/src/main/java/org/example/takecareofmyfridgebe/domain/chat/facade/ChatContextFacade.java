package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
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

/**
 * 이번 메시지를 처리하기 전에 상담 문맥(최근 대화와 카드 이름)을 읽는다.
 * 최근 메시지 1번, 그 메시지들의 카드 1번, 카드 레시피 이름 1번으로 끝난다 (메시지·카드 수와 상관없음).
 * 문맥을 못 읽어도 답은 지금처럼 이번 메시지만으로 할 수 있으므로, 실패하면 빈 문맥으로 계속한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextFacade {

    private final ChatMessageService chatMessageService;
    private final RecipeService recipeService;

    public ChatContext load(Long sessionId) {
        try {
            return new ChatContext(recentMessages(sessionId));
        } catch (RuntimeException e) {
            log.warn("상담 문맥 읽기 실패, 이전 대화 없이 계속 - sessionId: {}, 원인: {}", sessionId, e.toString());
            return ChatContext.empty();
        }
    }

    private List<ContextMessage> recentMessages(Long sessionId) {
        List<ChatMessageResponse> messages = chatMessageService.findRecentMessages(sessionId);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> assistantIds = messages.stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .map(ChatMessageResponse::id)
                .toList();
        Map<Long, List<SavedRecommendation>> cardsByMessage = chatMessageService.findRecipeRecommendations(assistantIds).stream()
                .collect(Collectors.groupingBy(SavedRecommendation::messageId));
        Map<Long, RecipeSummary> summaries = cardsByMessage.isEmpty()
                ? Map.of()
                : recipeService.findSummaries(cardsByMessage.values().stream()
                .flatMap(List::stream)
                .map(SavedRecommendation::recipeId)
                .distinct()
                .toList());

        List<ContextMessage> result = new ArrayList<>();
        for (ChatMessageResponse message : messages) {
            List<CardName> cards = cardsByMessage.getOrDefault(message.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(SavedRecommendation::displayOrder))
                    .map(card -> summaries.get(card.recipeId()))
                    .filter(Objects::nonNull)
                    .map(summary -> new CardName(summary.recipeId(), summary.name()))
                    .toList();
            result.add(new ContextMessage(message.role(), message.content(), cards));
        }
        return result;
    }
}

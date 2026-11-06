package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 상담 메시지 기록을 추천 카드(레시피·밀키트)와 함께 조회한다.
 * 카드는 AI 메시지 id 묶음으로 한 번에 읽고, 레시피·냉장고 계산도 레시피 id 묶음으로 한 번에 한다 (메시지 수와 상관없는 쿼리 수).
 */
@Component
@RequiredArgsConstructor
public class ChatMessageQueryFacade {

    private final ChatMessageService chatMessageService;
    private final ChatRecommendationFacade chatRecommendationFacade;

    public List<ChatMessageResponse> getMessages(Long memberId, Long sessionId) {
        List<ChatMessageResponse> messages = chatMessageService.getMessages(memberId, sessionId);
        List<Long> assistantIds = messages.stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .map(ChatMessageResponse::id)
                .toList();
        List<SavedRecommendation> saved = chatMessageService.findRecommendations(assistantIds);
        if (saved.isEmpty()) {
            return messages;
        }
        Map<Long, List<RecommendedRecipe>> cards = chatRecommendationFacade.rebuildCards(memberId, saved);
        Map<Long, List<RecommendedMealkit>> mealkits = chatRecommendationFacade.rebuildMealkitCards(saved);
        return messages.stream()
                .map(message -> message.withRecommendations(cards.getOrDefault(message.id(), List.of()),
                        mealkits.getOrDefault(message.id(), List.of())))
                .toList();
    }
}

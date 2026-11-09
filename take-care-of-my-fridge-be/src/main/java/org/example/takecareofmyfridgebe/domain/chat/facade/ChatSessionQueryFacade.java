package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse.CookedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse.RecommendedRecipeName;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 상담 조회에 다른 도메인 정보를 붙인다.
 * <ul>
 *   <li>상담 내역 목록: 상담마다 최근 추천 메뉴(최대 3개). 추천은 목록의 상담 id 묶음으로 한 번, 레시피 이름은 레시피 id 묶음으로 한 번 조회한다</li>
 *   <li>상담 단건: 요리 완료로 끝난 상담이면 그 요리 (요리 기록 1번 + 레시피 이름 1번)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ChatSessionQueryFacade {

    static final int RECOMMENDED_RECIPE_LIMIT = 3;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final RecipeService recipeService;
    private final CookingService cookingService;

    public ChatSessionListResponse getMySessions(Long memberId, ChatSessionListRequest request) {
        ChatSessionListResponse response = chatSessionService.getMySessions(memberId, request);
        PageResponse<ChatSessionSummaryResponse> page = response.sessions();
        List<Long> sessionIds = page.content().stream().map(ChatSessionSummaryResponse::sessionId).toList();
        Map<Long, List<Long>> recipeIdsBySession =
                chatMessageService.findRecentRecommendedRecipeIds(sessionIds, RECOMMENDED_RECIPE_LIMIT);
        if (recipeIdsBySession.isEmpty()) {
            return response;
        }
        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(
                recipeIdsBySession.values().stream().flatMap(List::stream).distinct().toList());

        List<ChatSessionSummaryResponse> content = page.content().stream()
                .map(session -> session.withRecommendedRecipes(
                        recipeIdsBySession.getOrDefault(session.sessionId(), List.of()).stream()
                                .map(summaries::get)
                                .filter(Objects::nonNull)
                                .map(summary -> new RecommendedRecipeName(summary.recipeId(), summary.name()))
                                .toList()))
                .toList();
        return new ChatSessionListResponse(response.counts(), new PageResponse<>(content, page.pageNumber(), page.pageSize(),
                page.totalElements(), page.totalPages(), page.isLast()));
    }

    // 끝난(COMPLETED) 상담만 요리 기록을 찾는다. 직접 끝낸 상담은 이은 요리 기록이 없어 null, 레시피가 지워졌어도 null
    public ChatSessionResponse getSession(Long memberId, Long sessionId) {
        ChatSessionResponse session = chatSessionService.getSession(memberId, sessionId);
        if (session.status() != ChatSessionStatus.COMPLETED) {
            return session;
        }
        return cookingService.findCookedRecipeId(memberId, sessionId)
                .map(recipeId -> recipeService.findSummaries(List.of(recipeId)).get(recipeId))
                .map(summary -> session.withCookedRecipe(new CookedRecipe(summary.recipeId(), summary.name())))
                .orElse(session);
    }
}

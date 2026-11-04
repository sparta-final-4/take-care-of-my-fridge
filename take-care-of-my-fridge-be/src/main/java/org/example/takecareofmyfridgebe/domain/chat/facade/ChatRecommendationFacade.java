package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.TasteLabel;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담 추천 답을 만들 재료를 모은다: 레시피 후보 검색 → 3개 고르기 → 카드 이벤트와 설명용 후보 정보.
 * 카드 이미지는 레시피 요약 조회(findSummaries)로 한 번에 가져온다. 구매 상품·저장은 아직 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRecommendationFacade {

    private final RecipeCandidateFacade recipeCandidateFacade;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;

    /**
     * @param content 사용자 메시지 원문 (설명 프롬프트에 넣는다)
     * @param query   의도 판단에서 만든 추천 조건
     * @return 카드 이벤트(후보가 없으면 empty=true)와 설명용 후보 정보
     */
    public RecommendationReply recommend(String content, RecipeQuery query) {
        RecipeCandidates found = recipeCandidateFacade.findCandidates(query);
        List<RecipeCandidate> candidates = found.candidates();
        logCandidates(query, candidates);
        List<RecipeCandidate> picked = RecommendationPicker.pick(candidates);

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(
                picked.stream().map(RecipeCandidate::recipeId).toList());
        List<RecommendedRecipe> recipes = picked.stream()
                .map(candidate -> toCard(candidate, summaries.get(candidate.recipeId())))
                .toList();

        String candidateInfo = RecommendationPromptBuilder.build(
                content, query, ingredientNames(query), picked, found.keywordsNotFound());
        return new RecommendationReply(ChatRecommendationsEventResponse.of(recipes), candidateInfo);
    }

    // 후보 10개의 정렬 단계별 값 (DEBUG). 정렬 순서: 요청 > 선택 > (선택이 있으면) 모자람 아님 > 임박 > 맛 > 보유 비율 > 없는 재료 수 > id
    private static void logCandidates(RecipeQuery query, List<RecipeCandidate> candidates) {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            RecipeCandidate candidate = candidates.get(i);
            RecipeCandidate.Ranking ranking = candidate.ranking();
            text.append(String.format("%n  %2d. %d %s [%s] | 요청 %d | 선택 %d | 모자람 %s | 임박 %d | 맛 %d | 보유 %d/%d | 없는 재료 %d | 냉장고에 없는 선택 %s",
                    i + 1, candidate.recipeId(), candidate.name(), candidate.category(),
                    ranking.requestMatches(), ranking.selectedMatches(), candidate.reason().lacksManyIngredients() ? "Y" : "N",
                    ranking.imminentScore(), ranking.tasteMatches(), ranking.ownedCount(), ranking.nonBasicIngredientCount(),
                    ranking.missingCount(), candidate.reason().missingSelectedIngredients()));
        }
        log.debug("상담 추천 후보 정렬 - memberId: {}, 선택 재료: {}, 선택 재료만: {}, 후보 {}개:{}",
                query.memberId(), query.selectedIngredientIds(), query.useSelectedOnly(), candidates.size(), text);
    }

    // 프롬프트에 쓸 선택·뺄 재료 이름
    private Map<Long, String> ingredientNames(RecipeQuery query) {
        List<Long> ids = Stream.concat(query.selectedIngredientIds().stream(), query.excludeIngredientIds().stream())
                .distinct()
                .toList();
        return ingredientService.getIngredientsByIds(ids).values().stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));
    }

    private static RecommendedRecipe toCard(RecipeCandidate candidate, RecipeSummary summary) {
        Reason reason = candidate.reason();
        // 원한 맛을 말했으면 그 맛, 아니면 회원 선호 맛과 겹친 맛
        List<Member.Taste> tastes = reason.matchedWantedTastes().isEmpty()
                ? reason.matchedTastes()
                : reason.matchedWantedTastes();
        return new RecommendedRecipe(
                candidate.recipeId(),
                candidate.name(),
                summary == null ? null : summary.imageUrl(),
                candidate.category(),
                candidate.cookTimeMin(),
                reason.usedFridgeIngredients(),
                reason.imminentIngredients().stream()
                        .map(imminent -> new ImminentIngredient(imminent.name(), imminent.dDay()))
                        .toList(),
                tastes.stream().map(TasteLabel::of).toList(),
                reason.missingIngredients().stream()
                        .map(missing -> new MissingIngredient(missing.ingredientId(), missing.name()))
                        .toList(),
                reason.missingSelectedIngredients(),
                reason.lacksManyIngredients(),
                RecommendationReasonWriter.write(candidate)
        );
    }
}

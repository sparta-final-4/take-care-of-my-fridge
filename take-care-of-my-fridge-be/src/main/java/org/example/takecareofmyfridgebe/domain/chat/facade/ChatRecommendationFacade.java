package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeDetailReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.TasteLabel;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    // 레시피 자세히 답에 붙는 카드 한 줄 이유 (설명 글이 없어 이 줄이 보인다)
    static final String DETAIL_CARD_REASON = "물어보신 레시피예요";

    private final RecipeCandidateFacade recipeCandidateFacade;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;

    /**
     * @param content 사용자 메시지 원문 (설명 프롬프트에 넣는다)
     * @param query   의도 판단에서 만든 추천 조건
     * @return 카드 이벤트(후보가 없으면 empty=true)와 설명용 후보 정보
     */
    public RecommendationReply recommend(String content, RecipeQuery query) {
        return recommend(content, query, List.of());
    }

    /**
     * 다시 추천. 직전 카드(previousCardIds)는 후보에서 빼고 고른다. 빼서 후보가 3개보다 적어지면 그때만 뒤에 다시 넣는다.
     * 새 추천은 previousCardIds 없이 부르고, 이미 추천한 레시피는 정렬에서 뒤로 간다 (RecipeQuery.alreadyRecommendedIds).
     *
     * @param previousCardIds 직전 추천 카드 레시피 ID ("다른 거 없어?"에서 같은 카드가 다시 나오지 않게)
     */
    public RecommendationReply recommend(String content, RecipeQuery query, Collection<Long> previousCardIds) {
        RecipeCandidates found = recipeCandidateFacade.findCandidates(query);
        List<RecipeCandidate> candidates = withoutPrevious(found.candidates(), previousCardIds);
        logCandidates(query, candidates);
        List<RecipeCandidate> picked = RecommendationPicker.pick(candidates);

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(
                picked.stream().map(RecipeCandidate::recipeId).toList());
        // 카드 이유는 앞 카드와 겹치지 않게 고른다 (프롬프트 후보 줄도 같은 이유를 쓴다)
        List<String> reasons = RecommendationReasonWriter.writeDistinct(picked, query.keywordText(), !query.hasRequest());
        List<RecommendedRecipe> recipes = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            RecipeCandidate candidate = picked.get(i);
            recipes.add(toCard(candidate, summaries.get(candidate.recipeId()), reasons.get(i), null));
        }
        logPicked(query, picked, recipes);

        String candidateInfo = RecommendationPromptBuilder.build(
                content, query, ingredientNames(query), picked, found.keywordsNotFound());
        // 안내 문장은 AI가 아니라 서버가 소개 맨 앞에 직접 보낸다 (후보가 없으면 정해진 답만 나가므로 붙이지 않는다)
        List<String> notices = picked.isEmpty()
                ? List.of()
                : RecommendationPromptBuilder.notices(query, picked, found.keywordsNotFound());
        return new RecommendationReply(ChatRecommendationsEventResponse.of(recipes), candidateInfo, notices);
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

    // 고른 레시피마다 어떻게 맞았는지(이름·늘린 이름·재료·쪼개짐·벡터·없음)와 카드 이유
    private static void logPicked(RecipeQuery query, List<RecipeCandidate> picked, List<RecommendedRecipe> recipes) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            RecipeCandidate candidate = picked.get(i);
            KeywordMatch match = candidate.reason().keywordMatch();
            lines.add(candidate.recipeId() + " " + candidate.name() + " [" + (match == null ? "없음" : match.type().label())
                    + "] " + recipes.get(i).reason());
        }
        log.info("상담 추천 후보 - memberId: {}, 키워드: {}, 고른 레시피: {}", query.memberId(), query.keywords(), lines);
    }

    /**
     * 레시피 자세히 답을 만들 재료: 그 레시피 카드 하나와 [레시피 정보]. 레시피가 지워졌으면 빈 값.
     * 카드의 임박·장볼 재료와 [냉장고 기준]은 지금 냉장고 기준으로 계산한다.
     */
    public Optional<RecipeDetailReply> describe(Long memberId, Long recipeId) {
        RecipeDetailResponse recipe;
        try {
            recipe = recipeService.getRecipe(recipeId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.RECIPE_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
        RecipeCandidate candidate = recipeCandidateFacade.describeRecipes(memberId, List.of(recipeId)).get(recipeId);
        String recipeInfo = RecipeDetailPromptBuilder.build(recipe, candidate);
        RecommendedRecipe card = candidate == null
                ? new RecommendedRecipe(recipe.recipeId(), recipe.name(), recipe.imageUrl(), recipe.category(),
                recipe.cookTimeMin(), List.of(), List.of(), List.of(), List.of(), List.of(), false, DETAIL_CARD_REASON, null)
                : toCard(candidate, new RecipeSummary(recipe.recipeId(), recipe.name(), recipe.imageUrl(), recipe.tipSummary()),
                DETAIL_CARD_REASON, null);
        return Optional.of(new RecipeDetailReply(ChatRecommendationsEventResponse.of(List.of(card)), recipeInfo));
    }

    // 직전 카드를 뺀 후보. 빼서 3개보다 적어지면 직전 카드를 정렬 순서대로 뒤에 다시 붙인다
    static List<RecipeCandidate> withoutPrevious(List<RecipeCandidate> candidates, Collection<Long> previousCardIds) {
        if (previousCardIds.isEmpty()) {
            return candidates;
        }
        Set<Long> previous = Set.copyOf(previousCardIds);
        List<RecipeCandidate> fresh = candidates.stream().filter(candidate -> !previous.contains(candidate.recipeId())).toList();
        if (fresh.size() >= RecommendationPicker.PICK_COUNT) {
            return fresh;
        }
        return Stream.concat(fresh.stream(), candidates.stream().filter(candidate -> previous.contains(candidate.recipeId())))
                .toList();
    }

    // 프롬프트에 쓸 선택·뺄 재료 이름
    private Map<Long, String> ingredientNames(RecipeQuery query) {
        List<Long> ids = Stream.concat(query.selectedIngredientIds().stream(), query.excludeIngredientIds().stream())
                .distinct()
                .toList();
        return ingredientService.getIngredientsByIds(ids).values().stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));
    }

    /**
     * 저장된 추천 카드를 다시 만든다. 순서·설명은 저장값, 이름·이미지·분류·시간은 지금 레시피, 임박·장볼 재료·이유는 지금 냉장고 기준.
     * 지워진 레시피는 빼고, "냉장고에 없는 선택 재료"는 고른 재료를 저장하지 않으므로 비운다. 레시피 조회는 메시지 수와 상관없이 한 번씩이다.
     *
     * @return AI 메시지 ID별 카드 (보여 준 순서)
     */
    public Map<Long, List<RecommendedRecipe>> rebuildCards(Long memberId, List<SavedRecommendation> saved) {
        if (saved.isEmpty()) {
            return Map.of();
        }
        List<Long> recipeIds = saved.stream().map(SavedRecommendation::recipeId).distinct().toList();
        Map<Long, RecipeCandidate> candidates = recipeCandidateFacade.describeRecipes(memberId, recipeIds);
        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(recipeIds);

        Map<Long, List<SavedRecommendation>> byMessage = saved.stream()
                .collect(Collectors.groupingBy(SavedRecommendation::messageId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<RecommendedRecipe>> cards = new LinkedHashMap<>();
        byMessage.forEach((messageId, recommendations) -> {
            List<SavedRecommendation> alive = recommendations.stream()
                    .sorted(Comparator.comparingInt(SavedRecommendation::displayOrder))
                    .filter(recommendation -> candidates.containsKey(recommendation.recipeId()))
                    .toList();
            List<RecipeCandidate> picked = alive.stream().map(r -> candidates.get(r.recipeId())).toList();
            List<String> reasons = RecommendationReasonWriter.writeDistinct(picked, null);
            List<RecommendedRecipe> messageCards = new ArrayList<>();
            for (int i = 0; i < alive.size(); i++) {
                RecipeCandidate candidate = picked.get(i);
                messageCards.add(toCard(candidate, summaries.get(candidate.recipeId()), reasons.get(i),
                        alive.get(i).description()));
            }
            cards.put(messageId, messageCards);
        });
        return cards;
    }

    private static RecommendedRecipe toCard(RecipeCandidate candidate, RecipeSummary summary, String cardReason,
                                            String description) {
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
                cardReason,
                description
        );
    }
}

package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
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
        List<RecipeCandidate> picked = RecommendationPicker.pick(recipeCandidateFacade.findCandidates(query).candidates());

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(
                picked.stream().map(RecipeCandidate::recipeId).toList());
        List<RecommendedRecipe> recipes = picked.stream()
                .map(candidate -> toCard(candidate, summaries.get(candidate.recipeId())))
                .toList();

        String candidateInfo = RecommendationPromptBuilder.build(content, query, ingredientNames(query), picked);
        return new RecommendationReply(ChatRecommendationsEventResponse.of(recipes), candidateInfo);
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
                reason.lacksManyIngredients()
        );
    }
}

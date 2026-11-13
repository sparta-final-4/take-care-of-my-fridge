package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 레시피 후보 검색 결과 (최대 10개, 모자라면 있는 만큼).
 *
 * @param fallback         선택 재료가 없거나 선택 재료와 겹치는 레시피가 없어 임박·보유 순으로 고른 결과인지
 * @param keywordsNotFound 요리 이름 키워드를 말했는데 레시피 검색으로 하나도 찾지 못했는지
 * @param keptAvoidTastes  피할 맛인데 사용자가 콕 집은 요리(요리 이름이 통째로 맞음)라 남긴 레시피 ID별 그 맛.
 *                         카드 이유에 "매운 요리예요"처럼 알린다
 * @param recipeTastes     후보 레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다). 요청 조건이 있는 추천에서 빈자리를 채울 때
 *                         반대 맛 레시피를 빼는 데 쓴다 (RecommendationPicker)
 * @param maybeSpicyIds    매울 수 있는 레시피 ID (매운 정도 MILD·HOT). 매운맛을 원했을 때 빈자리를 매운 레시피부터 채우는 데 쓴다
 * @param emptyReason      후보가 0개인 이유 (말한 요리를 못 찾았거나 뺄 재료 때문에 뺐을 때만). 그 밖이면 null
 */
public record RecipeCandidates(
        List<RecipeCandidate> candidates,
        boolean fallback,
        boolean keywordsNotFound,
        Map<Long, List<Member.Taste>> keptAvoidTastes,
        Map<Long, Set<Member.Taste>> recipeTastes,
        Set<Long> maybeSpicyIds,
        EmptyReason emptyReason
) {

    public RecipeCandidates {
        keptAvoidTastes = keptAvoidTastes == null ? Map.of() : Map.copyOf(keptAvoidTastes);
        recipeTastes = recipeTastes == null ? Map.of() : Map.copyOf(recipeTastes);
        maybeSpicyIds = maybeSpicyIds == null ? Set.of() : Set.copyOf(maybeSpicyIds);
    }

    /**
     * 요리 이름을 말했는데 카드가 0장인 이유. 상담 답 문구(AI 없이 서버 글)를 고르는 데 쓴다.
     *
     * @param type                못 찾음 / 뺄 재료 때문에 뺌
     * @param dish                사용자가 말한 요리 이름 그대로 (요리 이름, 없으면 첫 키워드)
     * @param excludedIngredients 뺀 요리에 들어 있는 뺄 재료 이름 (EXCLUDED일 때. 모르면 빈 목록)
     */
    public record EmptyReason(Type type, String dish, List<String> excludedIngredients) {

        public enum Type {
            // 요리 이름이 들어간 레시피가 없음
            NOT_FOUND,
            // 요리 이름이 들어간 레시피는 있지만 뺄 재료(알레르기·못 먹는 재료)가 들어 있어 뺌
            EXCLUDED
        }

        public EmptyReason {
            excludedIngredients = excludedIngredients == null ? List.of() : List.copyOf(excludedIngredients);
        }

        public static EmptyReason notFound(String dish) {
            return new EmptyReason(Type.NOT_FOUND, dish, List.of());
        }

        public static EmptyReason excluded(String dish, List<String> ingredientNames) {
            return new EmptyReason(Type.EXCLUDED, dish, ingredientNames);
        }
    }

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback, boolean keywordsNotFound,
                            Map<Long, List<Member.Taste>> keptAvoidTastes, Map<Long, Set<Member.Taste>> recipeTastes,
                            Set<Long> maybeSpicyIds) {
        this(candidates, fallback, keywordsNotFound, keptAvoidTastes, recipeTastes, maybeSpicyIds, null);
    }

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback, boolean keywordsNotFound,
                            Map<Long, List<Member.Taste>> keptAvoidTastes) {
        this(candidates, fallback, keywordsNotFound, keptAvoidTastes, null, null);
    }

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback, boolean keywordsNotFound) {
        this(candidates, fallback, keywordsNotFound, Map.of());
    }

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback) {
        this(candidates, fallback, false);
    }

    public RecipeCandidates withKeptAvoidTastes(Map<Long, List<Member.Taste>> kept) {
        return new RecipeCandidates(candidates, fallback, keywordsNotFound, kept, recipeTastes, maybeSpicyIds, emptyReason);
    }

    // 후보 레시피의 맛·매울 수 있는 레시피를 채운 결과
    public RecipeCandidates withTasteDetails(Map<Long, Set<Member.Taste>> tastes, Set<Long> maybeSpicy) {
        return new RecipeCandidates(candidates, fallback, keywordsNotFound, keptAvoidTastes, tastes, maybeSpicy, emptyReason);
    }

    // 후보가 0개인 이유를 채운 결과
    public RecipeCandidates withEmptyReason(EmptyReason reason) {
        return new RecipeCandidates(candidates, fallback, keywordsNotFound, keptAvoidTastes, recipeTastes, maybeSpicyIds, reason);
    }
}

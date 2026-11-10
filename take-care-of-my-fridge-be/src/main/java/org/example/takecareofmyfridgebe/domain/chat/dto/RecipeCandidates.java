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
 */
public record RecipeCandidates(
        List<RecipeCandidate> candidates,
        boolean fallback,
        boolean keywordsNotFound,
        Map<Long, List<Member.Taste>> keptAvoidTastes,
        Map<Long, Set<Member.Taste>> recipeTastes,
        Set<Long> maybeSpicyIds
) {

    public RecipeCandidates {
        keptAvoidTastes = keptAvoidTastes == null ? Map.of() : Map.copyOf(keptAvoidTastes);
        recipeTastes = recipeTastes == null ? Map.of() : Map.copyOf(recipeTastes);
        maybeSpicyIds = maybeSpicyIds == null ? Set.of() : Set.copyOf(maybeSpicyIds);
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
        return new RecipeCandidates(candidates, fallback, keywordsNotFound, kept, recipeTastes, maybeSpicyIds);
    }

    // 후보 레시피의 맛·매울 수 있는 레시피를 채운 결과
    public RecipeCandidates withTasteDetails(Map<Long, Set<Member.Taste>> tastes, Set<Long> maybeSpicy) {
        return new RecipeCandidates(candidates, fallback, keywordsNotFound, keptAvoidTastes, tastes, maybeSpicy);
    }
}

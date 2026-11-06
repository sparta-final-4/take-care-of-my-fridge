package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;
import java.util.Map;

/**
 * 레시피 후보 검색 결과 (최대 10개, 모자라면 있는 만큼).
 *
 * @param fallback         선택 재료가 없거나 선택 재료와 겹치는 레시피가 없어 임박·보유 순으로 고른 결과인지
 * @param keywordsNotFound 요리 이름 키워드를 말했는데 레시피 검색으로 하나도 찾지 못했는지
 * @param keptAvoidTastes  피할 맛인데 사용자가 콕 집은 요리(요리 이름이 통째로 맞음)라 남긴 레시피 ID별 그 맛.
 *                         카드 이유에 "매운 요리예요"처럼 알린다
 */
public record RecipeCandidates(
        List<RecipeCandidate> candidates,
        boolean fallback,
        boolean keywordsNotFound,
        Map<Long, List<Member.Taste>> keptAvoidTastes
) {

    public RecipeCandidates {
        keptAvoidTastes = keptAvoidTastes == null ? Map.of() : Map.copyOf(keptAvoidTastes);
    }

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback, boolean keywordsNotFound) {
        this(candidates, fallback, keywordsNotFound, Map.of());
    }

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback) {
        this(candidates, fallback, false);
    }

    public RecipeCandidates withKeptAvoidTastes(Map<Long, List<Member.Taste>> kept) {
        return new RecipeCandidates(candidates, fallback, keywordsNotFound, kept);
    }
}

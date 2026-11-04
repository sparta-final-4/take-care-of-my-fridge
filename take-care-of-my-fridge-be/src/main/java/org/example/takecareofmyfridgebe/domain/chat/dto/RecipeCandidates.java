package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.List;

/**
 * 레시피 후보 검색 결과 (최대 10개, 모자라면 있는 만큼).
 *
 * @param fallback         선택 재료가 없거나 선택 재료와 겹치는 레시피가 없어 임박·보유 순으로 고른 결과인지
 * @param keywordsNotFound 요리 이름 키워드를 말했는데 레시피 검색으로 하나도 찾지 못했는지
 */
public record RecipeCandidates(
        List<RecipeCandidate> candidates,
        boolean fallback,
        boolean keywordsNotFound
) {

    public RecipeCandidates(List<RecipeCandidate> candidates, boolean fallback) {
        this(candidates, fallback, false);
    }
}

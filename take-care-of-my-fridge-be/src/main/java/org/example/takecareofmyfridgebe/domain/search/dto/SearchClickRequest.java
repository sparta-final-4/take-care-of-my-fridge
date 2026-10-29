package org.example.takecareofmyfridgebe.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchClickHistory;

public record SearchClickRequest(
        @NotNull(message = "클릭한 상품을 알려 주세요.")
        @Positive(message = "상품 ID는 1 이상이어야 합니다.")
        Long productId,

        @NotBlank(message = "검색어를 입력해 주세요.")
        @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
        String keyword
) {

    // 비로그인이면 memberId는 null이다. 검색어는 앞뒤 공백을 뺀다.
    public SearchClickHistory toEntity(Long memberId) {
        return SearchClickHistory.builder()
                .productId(productId)
                .memberId(memberId)
                .keyword(keyword.strip())
                .build();
    }
}

package org.example.takecareofmyfridgebe.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchSuggestionRequest(
        @NotBlank(message = "검색어를 입력해 주세요.")
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword
) {
    public SearchSuggestionRequest {
        keyword = keyword == null ? null : keyword.strip();
    }
}

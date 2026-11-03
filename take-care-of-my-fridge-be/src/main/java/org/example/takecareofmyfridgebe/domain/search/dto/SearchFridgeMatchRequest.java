package org.example.takecareofmyfridgebe.domain.search.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// 통합검색 결과 한 페이지의 레시피 ID와 검색어. 검색어는 선택이다.
public record SearchFridgeMatchRequest(
        @NotEmpty(message = "레시피를 알려 주세요.")
        @Size(max = 20, message = "레시피는 한 번에 20개까지 볼 수 있습니다.")
        List<Long> recipeIds,

        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword
) {
    public SearchFridgeMatchRequest {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }
}

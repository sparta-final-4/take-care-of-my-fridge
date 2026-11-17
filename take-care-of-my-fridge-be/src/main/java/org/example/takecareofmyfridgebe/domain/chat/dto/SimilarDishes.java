package org.example.takecareofmyfridgebe.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

/**
 * AI에게 받은 요리 이름 목록 (SimilarDishFinder가 캐시하고 넘긴다).
 *
 * @param formalName 받은 요리 이름이 줄임말일 때의 정식 이름 (비슷한 요리만. 줄임말이 아니거나 모르면 null)
 * @param dishes     비슷한 요리 이름 또는 넓은 요청에 맞는 요리 이름. 없으면 빈 목록
 */
public record SimilarDishes(
        String formalName,
        List<String> dishes
) {

    public static final SimilarDishes NONE = new SimilarDishes(null, List.of());

    public SimilarDishes {
        formalName = formalName == null || formalName.isBlank() ? null : formalName.strip();
        dishes = dishes == null ? List.of() : dishes.stream().filter(Objects::nonNull).toList();
    }

    // 정식 이름 없이 요리 이름 목록만 (넓은 요청 요리)
    public static SimilarDishes of(List<String> dishes) {
        return new SimilarDishes(null, dishes);
    }

    // 정식 이름도 요리 이름도 없는지 (캐시에 두지 않는다). 캐시 JSON 칸이 아니다
    @JsonIgnore
    public boolean isEmpty() {
        return formalName == null && dishes.isEmpty();
    }
}

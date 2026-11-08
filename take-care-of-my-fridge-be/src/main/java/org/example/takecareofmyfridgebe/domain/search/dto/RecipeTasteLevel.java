package org.example.takecareofmyfridgebe.domain.search.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Set;

/**
 * 레시피 인덱스에 색인된 맛(tastes)과 매운 정도(spicy_level). 색인할 때 HOT이면 맛에 SPICY를 더해 두 값이 맞는다.
 *
 * @param tastes     레시피 맛 (확실히 매운 재료가 있으면 SPICY가 들어 있다)
 * @param spicyLevel 재료로 가른 매운 정도
 */
public record RecipeTasteLevel(Set<Member.Taste> tastes, RecipeSpicyLevel spicyLevel) {

    public RecipeTasteLevel {
        tastes = tastes == null || tastes.isEmpty() ? Set.of() : Set.copyOf(tastes);
        spicyLevel = spicyLevel == null ? RecipeSpicyLevel.NONE : spicyLevel;
    }

    // 확실히 매움 ("매운 걸로"면 원하는 맛과 맞은 것으로 센다)
    public boolean definitelySpicy() {
        return spicyLevel == RecipeSpicyLevel.HOT;
    }

    // 매울 수 있음 (MILD 이상. "매운 거 말고"면 뺀다)
    public boolean maybeSpicy() {
        return spicyLevel != RecipeSpicyLevel.NONE;
    }
}

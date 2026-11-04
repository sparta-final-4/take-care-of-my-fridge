package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 정렬된 레시피 후보에서 추천할 3개를 고른다. 스프링에 의존하지 않는 순수 클래스.
 * 위에서 3개를 고르되, 3개가 모두 같은 분류면 4~10위에서 처음 나오는 다른 분류 레시피로 3위를 바꾼다.
 * 바꿔 넣은 카드는 alternative(다른 제안)로 표시해 AI 설명·카드 이유가 요청과 다른 제안이라고 말하게 한다.
 * 비슷한 요리만 세 개 보이지 않게 하려는 것이다. 후보가 3개보다 적으면 있는 만큼 고른다.
 */
public final class RecommendationPicker {

    public static final int PICK_COUNT = 3;

    private RecommendationPicker() {
    }

    public static List<RecipeCandidate> pick(List<RecipeCandidate> ranked) {
        List<RecipeCandidate> picked = new ArrayList<>(ranked.subList(0, Math.min(PICK_COUNT, ranked.size())));
        if (picked.size() < PICK_COUNT || !allSameCategory(picked)) {
            return List.copyOf(picked);
        }
        String category = picked.get(0).category();
        ranked.stream()
                .skip(PICK_COUNT)
                .filter(candidate -> candidate.category() != null && !candidate.category().equals(category))
                .findFirst()
                .ifPresent(other -> picked.set(PICK_COUNT - 1, other.asAlternative()));
        return List.copyOf(picked);
    }

    // 분류가 없는 레시피가 끼어 있으면 같은 분류로 보지 않는다
    private static boolean allSameCategory(List<RecipeCandidate> picked) {
        String category = picked.get(0).category();
        return category != null && picked.stream().allMatch(candidate -> Objects.equals(candidate.category(), category));
    }
}

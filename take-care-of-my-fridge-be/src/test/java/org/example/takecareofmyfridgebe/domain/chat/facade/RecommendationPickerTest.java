package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPickerTest {

    @Test
    @DisplayName("분류가 섞여 있으면 위에서 3개를 그대로 고른다")
    void picksTopThree_whenCategoriesDiffer() {
        List<RecipeCandidate> picked = RecommendationPicker.pick(List.of(
                candidate(1L, "국"), candidate(2L, "찜"), candidate(3L, "국"), candidate(4L, "볶음")));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("3개가 모두 같은 분류면 4~10위에서 처음 나오는 다른 분류로 3위를 바꾼다")
    void replacesThird_whenTopThreeShareCategory() {
        List<RecipeCandidate> picked = RecommendationPicker.pick(List.of(
                candidate(1L, "국"), candidate(2L, "국"), candidate(3L, "국"),
                candidate(4L, "국"), candidate(5L, null), candidate(6L, "볶음"), candidate(7L, "찜")));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 6L);
    }

    @Test
    @DisplayName("3개가 같은 분류여도 아래에 다른 분류가 없으면 그대로 둔다")
    void keepsTopThree_whenNoOtherCategory() {
        List<RecipeCandidate> picked = RecommendationPicker.pick(List.of(
                candidate(1L, "국"), candidate(2L, "국"), candidate(3L, "국"), candidate(4L, "국")));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("후보가 3개보다 적으면 있는 만큼, 없으면 빈 목록을 고른다")
    void picksAvailable_whenFewerThanThree() {
        assertThat(RecommendationPicker.pick(List.of(candidate(1L, "국"), candidate(2L, "국"))))
                .extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L);
        assertThat(RecommendationPicker.pick(List.of())).isEmpty();
    }

    private static RecipeCandidate candidate(Long id, String category) {
        return new RecipeCandidate(id, "레시피" + id, category, 20,
                new Ranking(0, 0, 0, 0, 1, 1, 0),
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of()));
    }
}

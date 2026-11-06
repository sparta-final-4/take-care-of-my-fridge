package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
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
        assertThat(picked).noneMatch(RecipeCandidate::alternative);
    }

    @Test
    @DisplayName("3개가 모두 같은 분류면 4~10위에서 처음 나오는 다른 분류로 3위를 바꾼다")
    void replacesThird_whenTopThreeShareCategory() {
        List<RecipeCandidate> picked = RecommendationPicker.pick(List.of(
                candidate(1L, "국"), candidate(2L, "국"), candidate(3L, "국"),
                candidate(4L, "국"), candidate(5L, null), candidate(6L, "볶음"), candidate(7L, "찜")));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 6L);
        // 바꿔 넣은 카드만 다른 제안으로 표시한다
        assertThat(picked).extracting(RecipeCandidate::alternative).containsExactly(false, false, true);
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

    @Test
    @DisplayName("키워드로 맞은 레시피가 두 키워드 이상이면 한 키워드 결과가 많아도 키워드마다 하나씩 먼저 고르고 남는 자리는 정렬 순서로 채운다")
    void spreadsCardsByKeyword_whenManyKeywordsMatched() {
        List<RecipeCandidate> ranked = List.of(
                keyword(1L, "면류", "볶음면"), keyword(2L, "면류", "볶음면"), keyword(3L, "면류", "볶음면"),
                keyword(4L, "튀김", "탕수육"), candidate(5L, "찜"));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of("탕짜면", "짜장면", "탕수육", "볶음면"));

        // AI 키워드 순서(탕수육 → 볶음면)로 하나씩, 남는 자리는 정렬 순서(2번 볶음면)
        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(4L, 1L, 2L);
    }

    @Test
    @DisplayName("요리 이름이 맞은 레시피는 키워드별로 고를 때 맨 앞에 둔다")
    void putsDishNameMatchFirst() {
        RecipeCandidate byName = new RecipeCandidate(9L, "탕짜면", "면류", 20, new Ranking(1, 0, 0, 0, 1, 1, 0),
                reason(KeywordMatch.of(KeywordMatch.Type.NAME)));
        List<RecipeCandidate> ranked = List.of(keyword(1L, "튀김", "탕수육"), keyword(2L, "면류", "볶음면"), byName);

        assertThat(RecommendationPicker.pick(ranked, List.of("탕수육", "볶음면")))
                .extracting(RecipeCandidate::recipeId).containsExactly(9L, 1L, 2L);
    }

    @Test
    @DisplayName("키워드별로 고른 자리는 같은 분류여도 바꾸지 않고, 정렬 순서로 채운 자리만 다른 분류로 바꾼다")
    void replacesOnlyFilledSlot_whenAllSameCategory() {
        List<RecipeCandidate> ranked = List.of(
                keyword(1L, "튀김", "탕수육"), keyword(2L, "튀김", "깐풍기"), candidate(3L, "튀김"), candidate(4L, "찜"));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of("김피탕", "탕수육", "깐풍기"));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 4L);
        assertThat(picked.get(0).alternative()).isFalse();
        assertThat(picked.get(1).alternative()).isFalse();
    }

    @Test
    @DisplayName("세 자리를 모두 키워드별로 골랐으면 같은 분류여도 바꾸지 않는다")
    void keepsKeywordSlots_whenAllPickedByKeyword() {
        List<RecipeCandidate> ranked = List.of(
                keyword(1L, "튀김", "탕수육"), keyword(2L, "튀김", "깐풍기"), keyword(3L, "튀김", "꿔바로우"), candidate(4L, "찜"));

        assertThat(RecommendationPicker.pick(ranked, List.of("탕수육", "깐풍기", "꿔바로우")))
                .extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("키워드로 맞은 레시피가 한 키워드뿐이면 지금처럼 위에서 3개를 고른다")
    void picksTopThree_whenOneKeywordMatched() {
        List<RecipeCandidate> ranked = List.of(
                keyword(1L, "면류", "볶음면"), keyword(2L, "면류", "볶음면"), candidate(3L, "찜"), keyword(4L, "면류", "볶음면"));

        assertThat(RecommendationPicker.pick(ranked, List.of("볶음면")))
                .extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("같은 분류 3개라 3위를 바꿀 때 키워드로 맞은 레시피가 들어오면 다른 제안으로 표시하지 않는다")
    void keepsKeywordMatchNotAlternative_whenReplaced() {
        List<RecipeCandidate> ranked = List.of(
                keyword(1L, "면류", "볶음면"), keyword(2L, "면류", "볶음면"), keyword(3L, "면류", "볶음면"), keyword(4L, "튀김", "탕수육"));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked);

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 4L);
        assertThat(picked.get(2).alternative()).isFalse();
    }

    // 늘린 이름(키워드)으로 맞은 후보
    private static RecipeCandidate keyword(Long id, String category, String keyword) {
        return new RecipeCandidate(id, "레시피" + id, category, 20, new Ranking(1, 0, 0, 0, 1, 1, 0),
                reason(new KeywordMatch(KeywordMatch.Type.EXPANDED, keyword, null)));
    }

    private static Reason reason(KeywordMatch match) {
        return new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(), List.of(), List.of(), match);
    }

    private static RecipeCandidate candidate(Long id, String category) {
        return new RecipeCandidate(id, "레시피" + id, category, 20,
                new Ranking(0, 0, 0, 0, 1, 1, 0),
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(), List.of(), List.of(), null));
    }
}

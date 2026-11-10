package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    @DisplayName("분류와 원하는 맛을 말했는데 둘 다 맞은 후보가 2개이고 채울 후보가 없으면 카드를 2장만 고른다")
    void picksOnlyTwo_whenTwoMatchAllAndNothingToFill() {
        List<RecipeCandidate> ranked = List.of(
                requested(1L, "국", 2), requested(2L, "국", 2), requested(3L, "무침", 0), requested(4L, "볶음", 0));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of(), spicySoupRule(Map.of(), Set.of()));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("모두 맞은 후보가 모자라면 분류가 맞는 후보 중 매운 레시피, 매울 수 있는 레시피 순으로 채운다")
    void fillsWithSpicyFirst_whenMatchAllIsShort() {
        // 3·4·5는 분류만 맞음. 정렬은 3 → 4 → 5지만 4는 매울 수 있고 5는 확실히 맵다
        List<RecipeCandidate> ranked = List.of(
                requested(1L, "국", 2), requested(3L, "국", 1), requested(4L, "국", 1), requested(5L, "국", 1));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of(),
                spicySoupRule(Map.of(5L, Set.of(Taste.SPICY)), Set.of(4L, 5L)));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 5L, 4L);
    }

    @Test
    @DisplayName("매운맛을 원했으면 순한맛만 있는 레시피는 분류가 맞아도 빈자리를 채우지 않는다")
    void skipsOppositeTaste_whenFilling() {
        List<RecipeCandidate> ranked = List.of(requested(1L, "국", 2), requested(2L, "국", 2), requested(3L, "국", 1));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of(),
                spicySoupRule(Map.of(3L, Set.of(Taste.MILD)), Set.of()));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("분류를 말했으면 3개가 모두 같은 분류여도 다른 분류로 바꾸지 않는다")
    void keepsSameCategory_whenCategorySpoken() {
        List<RecipeCandidate> ranked = List.of(
                requested(1L, "국", 2), requested(2L, "국", 2), requested(3L, "국", 2), requested(4L, "찌개", 2));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of(), spicySoupRule(Map.of(), Set.of()));

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 3L);
        assertThat(picked).noneMatch(RecipeCandidate::alternative);
    }

    @Test
    @DisplayName("원하는 맛만 말했으면 모두 같은 분류일 때 맛이 맞는 다른 분류로 바꾼다")
    void replacesWithMatchingCategory_whenOnlyTasteSpoken() {
        PickRule rule = new PickRule(1, Set.of(), Set.of(Taste.SPICY), Set.of(Taste.MILD), Map.of(), Set.of());
        List<RecipeCandidate> ranked = List.of(
                requested(1L, "국", 1), requested(2L, "국", 1), requested(3L, "국", 1), requested(4L, "볶음", 1));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of(), rule);

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 4L);
        assertThat(picked.get(2).alternative()).isTrue();
    }

    @Test
    @DisplayName("말한 조건이 없으면 요청 일치와 상관없이 정렬 순서대로 3장을 고른다")
    void picksTopThree_whenNoRequest() {
        List<RecipeCandidate> ranked = List.of(
                requested(1L, "국", 0), requested(2L, "찜", 0), requested(3L, "볶음", 0), requested(4L, "국", 0));

        List<RecipeCandidate> picked = RecommendationPicker.pick(ranked, List.of(), PickRule.NONE);

        assertThat(picked).extracting(RecipeCandidate::recipeId).containsExactly(1L, 2L, 3L);
    }

    // "맵고 따뜻한 국물": 원하는 맛 매운맛 + 분류 국 (조건 2개)
    private static PickRule spicySoupRule(Map<Long, Set<Taste>> tastes, Set<Long> maybeSpicy) {
        return new PickRule(2, Set.of("국"), Set.of(Taste.SPICY), Set.of(Taste.MILD), tastes, maybeSpicy);
    }

    // 요청 일치 수만 정한 후보
    private static RecipeCandidate requested(Long id, String category, int requestMatches) {
        return new RecipeCandidate(id, "레시피" + id, category, 20,
                new Ranking(requestMatches, 0, 0, 0, 1, 1, 0),
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(), List.of(), List.of(), null));
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

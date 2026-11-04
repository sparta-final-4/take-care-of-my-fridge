package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch.Type;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationReasonWriterTest {

    @Test
    @DisplayName("사용자가 말한 이름이 레시피 이름에 있으면 다른 근거보다 먼저 찾으신 요리라고 쓴다")
    void writesName() {
        assertThat(writeKeyword(KeywordMatch.of(Type.NAME), "냉면", "면류")).isEqualTo("찾으신 냉면이에요");
        assertThat(writeKeyword(KeywordMatch.of(Type.NAME), "김치찌개", "찌개")).isEqualTo("찾으신 김치찌개예요");
    }

    @Test
    @DisplayName("AI가 늘린 키워드가 이름에 있으면 사용자가 말한 이름과 비슷한 그 요리라고 쓴다")
    void writesExpanded() {
        assertThat(writeKeyword(new KeywordMatch(Type.EXPANDED, "막국수", null), "냉면", "면류"))
                .isEqualTo("냉면과 비슷한 막국수예요");
    }

    @Test
    @DisplayName("키워드 속 재료가 레시피 재료에 있으면 그 재료를 쓰는 요리라고 쓴다")
    void writesIngredient() {
        assertThat(writeKeyword(new KeywordMatch(Type.INGREDIENT, "냉메밀", "메밀면"), "냉면", "면류"))
                .isEqualTo("냉면처럼 메밀면을 쓰는 요리예요");
    }

    @Test
    @DisplayName("단어가 쪼개져 걸렸으면 사용자가 말한 이름과 비슷한 분류 요리라고 쓴다")
    void writesSplit() {
        assertThat(writeKeyword(KeywordMatch.of(Type.SPLIT), "냉면", "면류")).isEqualTo("냉면과 비슷한 면 요리예요");
    }

    @Test
    @DisplayName("벡터 검색으로만 찾았으면 찾으신·비슷한을 쓰지 않고 선택 재료·임박·맛으로 쓴다")
    void skipsKeyword_whenVectorOnly() {
        Reason reason = new Reason(List.of("고추"), List.of(new ImminentIngredient("고추", 1)), List.of(), List.of(),
                List.of("분류: 면류"), false, false, List.of(), List.of(), List.of(), KeywordMatch.of(Type.VECTOR));

        assertThat(RecommendationReasonWriter.write(candidate("면류", reason), "냉면")).isEqualTo("내일까지인 고추를 쓸 수 있어요");
    }

    @Test
    @DisplayName("요리 이름 없이 특징만 말했으면(차가운 요리) 비교 없이 맞은 키워드·재료·분류만 쓴다")
    void writesWithoutComparison_whenNoDishText() {
        assertThat(writeKeyword(new KeywordMatch(Type.EXPANDED, "냉국", null), null, "국")).isEqualTo("냉국 요리예요");
        assertThat(writeKeyword(new KeywordMatch(Type.INGREDIENT, "냉메밀", "메밀면"), null, "면류"))
                .isEqualTo("메밀면을 쓰는 요리예요");
        assertThat(writeKeyword(KeywordMatch.of(Type.SPLIT), null, "면류")).isEqualTo("면 요리예요");
    }

    @Test
    @DisplayName("키워드가 없으면 원하는 맛과 분류로 쓴다")
    void writesWantedTasteAndCategory() {
        assertThat(write(reason(List.of("맛: SPICY", "분류: 찌개"), List.of(Taste.SPICY), List.of(), List.of(), List.of())))
                .isEqualTo("매콤한 찌개 요리예요");
        assertThat(write(reason(List.of("분류: 면류"), List.of(), List.of(), List.of(), List.of())))
                .isEqualTo("면류 요리예요");
        assertThat(write(reason(List.of("맛: SPICY"), List.of(Taste.SPICY), List.of(), List.of(), List.of())))
                .isEqualTo("매콤한 요리예요");
    }

    @Test
    @DisplayName("요청이 없으면 선택 재료가 주재료라고 쓰고 받침에 맞는 조사를 붙인다")
    void writesSelectedIngredients() {
        assertThat(write(reason(List.of(), List.of(), List.of("느타리버섯", "두부"), List.of(), List.of())))
                .isEqualTo("느타리버섯·두부가 주재료예요");
        assertThat(write(reason(List.of(), List.of(), List.of("버섯"), List.of(), List.of())))
                .isEqualTo("버섯이 주재료예요");
    }

    @Test
    @DisplayName("요청·선택 재료가 없으면 가장 급한 임박 재료를 쓸 수 있다고 쓴다")
    void writesMostUrgentImminent() {
        Reason reason = reason(List.of(), List.of(), List.of(),
                List.of(new ImminentIngredient("대파", 2), new ImminentIngredient("고추", 1)), List.of(Taste.SALTY));

        assertThat(write(reason)).isEqualTo("내일까지인 고추를 쓸 수 있어요");
    }

    @Test
    @DisplayName("임박 재료도 없으면 겹친 맛을 두 개까지 이어 쓴다")
    void writesMatchedTastes() {
        assertThat(write(reason(List.of(), List.of(), List.of(), List.of(), List.of(Taste.SALTY, Taste.UMAMI, Taste.SWEET))))
                .isEqualTo("짭짤하고 감칠맛 나요");
    }

    @Test
    @DisplayName("근거가 하나도 없으면 쓰는 냉장고 재료 수를 쓴다")
    void writesUsedCount_whenNoReason() {
        Reason reason = new Reason(List.of("양파", "대파", "마늘"), List.of(), List.of(), List.of(), List.of(), false, false,
                List.of(), List.of(), List.of(), null);

        assertThat(write(reason)).isEqualTo("냉장고 재료 3가지를 써요");
    }

    private static String write(Reason reason) {
        return RecommendationReasonWriter.write(candidate("국", reason), null);
    }

    private static String writeKeyword(KeywordMatch match, String dishText, String category) {
        Reason reason = new Reason(List.of("양파"), List.of(new ImminentIngredient("양파", 0)), List.of(), List.of(),
                List.of(), false, false, List.of(), List.of(), List.of("두부"), match);
        return RecommendationReasonWriter.write(candidate(category, reason), dishText);
    }

    private static RecipeCandidate candidate(String category, Reason reason) {
        return new RecipeCandidate(1L, "레시피", category, 20, new Ranking(0, 0, 0, 0, 1, 1, 0), reason);
    }

    private static Reason reason(List<String> requests, List<Taste> wantedTastes, List<String> usedSelected,
                                 List<ImminentIngredient> imminent, List<Taste> tastes) {
        return new Reason(List.of("양파"), imminent, tastes, wantedTastes, requests, false, false, List.of(), List.of(),
                usedSelected, null);
    }

    @Test
    @DisplayName("고른 카드끼리 이유가 겹치면 다음 카드는 다른 임박 재료, 겹친 맛, 냉장고 재료 수 순으로 바꾼다")
    void writesDistinctReasons() {
        List<ImminentIngredient> imminent = List.of(new ImminentIngredient("고추", 1), new ImminentIngredient("깻잎", 1));
        List<String> used = List.of("고추", "깻잎", "대파", "양파");
        RecipeCandidate eel = candidate("조림", new Reason(used, imminent, List.of(), List.of(), List.of(), false, false,
                List.of(), List.of(), List.of(), null));
        RecipeCandidate kimchi = candidate("김치", new Reason(used, imminent, List.of(), List.of(), List.of(), false, false,
                List.of(), List.of(), List.of(), null));
        RecipeCandidate rice = candidate("밥", new Reason(used, imminent, List.of(Taste.SALTY, Taste.UMAMI), List.of(),
                List.of(), false, false, List.of(), List.of(), List.of(), null));
        RecipeCandidate plain = candidate("밥", new Reason(used, imminent, List.of(), List.of(), List.of(), false, false,
                List.of(), List.of(), List.of(), null));

        assertThat(RecommendationReasonWriter.writeDistinct(List.of(eel, kimchi, rice), null)).containsExactly(
                "내일까지인 고추를 쓸 수 있어요", "내일까지인 깻잎을 쓸 수 있어요", "짭짤하고 감칠맛 나요");
        assertThat(RecommendationReasonWriter.writeDistinct(List.of(eel, kimchi, plain), null).get(2))
                .isEqualTo("냉장고 재료 4가지를 써요");
    }
}

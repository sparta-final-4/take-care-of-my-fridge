package org.example.takecareofmyfridgebe.domain.chat.facade;

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
    @DisplayName("키워드가 맞으면 다른 근거보다 먼저 찾으신 요리라고 쓴다")
    void writesKeywordFirst() {
        Reason reason = reason(List.of("키워드: 냉면, 막국수", "분류: 면류"), List.of(), List.of("두부"),
                List.of(new ImminentIngredient("고추", 1)), List.of(Taste.SALTY));

        assertThat(write(reason)).isEqualTo("찾으신 냉면 요리예요");
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
                List.of(), List.of(), List.of());

        assertThat(write(reason)).isEqualTo("냉장고 재료 3가지를 써요");
    }

    private static String write(Reason reason) {
        return RecommendationReasonWriter.write(
                new RecipeCandidate(1L, "레시피", "국", 20, new Ranking(0, 0, 0, 0, 1, 1, 0), reason));
    }

    private static Reason reason(List<String> requests, List<Taste> wantedTastes, List<String> usedSelected,
                                 List<ImminentIngredient> imminent, List<Taste> tastes) {
        return new Reason(List.of("양파"), imminent, tastes, wantedTastes, requests, false, false, List.of(), List.of(),
                usedSelected);
    }
}

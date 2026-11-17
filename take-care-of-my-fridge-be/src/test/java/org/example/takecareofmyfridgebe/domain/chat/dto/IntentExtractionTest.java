package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentExtractionTest {

    @Test
    @DisplayName("말하지 않은 칸(null)은 정리한 뒤에도 null로 둔다")
    void keepsNull_whenFieldIsNotSaid() {
        IntentExtraction result = IntentExtraction.empty().sanitize();

        assertThat(result).isEqualTo(IntentExtraction.empty());
    }

    @Test
    @DisplayName("조리 시간이 0 이하면 null로 바꾼다")
    void returnsNullMaxMinutes_whenNotPositive() {
        assertThat(extraction(0, null, null, null, null).sanitize().maxMinutes()).isNull();
        assertThat(extraction(30, null, null, null, null).sanitize().maxMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("재료 이름은 앞뒤 공백을 지우고 빈 이름·null·중복을 뺀다")
    void trimsIngredientNames() {
        IntentExtraction result = new IntentExtraction(null, Arrays.asList(" 두부 ", "", null, "두부", "버섯"), List.of("오이 "),
                true, null, null, null, null, null, null, null, null).sanitize();

        assertThat(result.includeIngredients()).containsExactly("두부", "버섯");
        assertThat(result.excludeIngredients()).containsExactly("오이");
        assertThat(result.useSelectedOnly()).isTrue();
    }

    @Test
    @DisplayName("키워드는 앞뒤 공백·빈 값·중복을 빼고 6개까지만 남기고, 빈 요리 표현은 null로 바꾼다")
    void trimsKeywordsToSix() {
        IntentExtraction result = extraction(null,
                List.of(" 냉면 ", "막국수", "", "냉면", "콩국수", "냉메밀", "냉파스타", "비빔국수", "물회"), "  ", " ", null).sanitize();

        assertThat(result.keywords()).containsExactly("냉면", "막국수", "콩국수", "냉메밀", "냉파스타", "비빔국수");
        assertThat(result.dishText()).isNull();
        assertThat(result.preferenceText()).isNull();
    }

    @Test
    @DisplayName("분류 이름인 키워드도 정리에서는 그대로 둔다 (분류 이름 빼기는 서버 보정 IntentCorrector가 한 곳에서 한다)")
    void keepsCategoryKeywords_whenSanitizing() {
        assertThat(extraction(null, List.of("국", "찌개"), null, null, null).sanitize().keywords()).containsExactly("국", "찌개");
    }

    @Test
    @DisplayName("레시피 자세히 대상은 카드 번호가 0 이하면 null, 이름이 비어 있으면 null로 정리한다")
    void sanitizesTargetFields() {
        IntentExtraction picked = detail(2, " 깐풍기 ", null).sanitize();
        IntentExtraction empty = detail(0, " ", null).sanitize();

        assertThat(picked.targetIndex()).isEqualTo(2);
        assertThat(picked.targetName()).isEqualTo("깐풍기");
        assertThat(empty.targetIndex()).isNull();
        assertThat(empty.targetName()).isNull();
    }

    @Test
    @DisplayName("뺄 조건(removed)은 정해진 종류만 남기고 소문자는 대문자로 맞춘다")
    void sanitizesRemovedConditions() {
        IntentExtraction result = extraction(null, null, null, null, List.of("taste", "MAX_MINUTES", "없는 조건")).sanitize();

        assertThat(result.removed()).containsExactly("TASTE", "MAX_MINUTES");
    }

    @Test
    @DisplayName("레시피 자세히 질문 종류·냉장고 범위는 정해진 이름만 남기고 소문자는 대문자로 맞추며, 모르는 값이면 null이다")
    void sanitizesNamedValues() {
        assertThat(detail(2, null, " how_to ").sanitize().detailQuestion()).isEqualTo("HOW_TO");
        assertThat(detail(2, null, "STEP").sanitize().detailQuestion()).isNull();
        assertThat(scope(" imminent ").sanitize().fridgeScope()).isEqualTo("IMMINENT");
        assertThat(scope("FREEZER").sanitize().fridgeScope()).isNull();
    }

    // 시간·키워드·요리 이름·원한 표현·뺄 조건만 정한 꺼내기 결과
    private static IntentExtraction extraction(Integer maxMinutes, List<String> keywords, String dishText, String preferenceText,
                                               List<String> removed) {
        return new IntentExtraction(maxMinutes, null, null, null, keywords, dishText, preferenceText, null, null, removed, null, null);
    }

    private static IntentExtraction detail(Integer targetIndex, String targetName, String question) {
        return new IntentExtraction(null, null, null, null, null, null, null, targetIndex, targetName, null, question, null);
    }

    private static IntentExtraction scope(String fridgeScope) {
        return new IntentExtraction(null, null, null, null, null, null, null, null, null, null, null, fridgeScope);
    }
}

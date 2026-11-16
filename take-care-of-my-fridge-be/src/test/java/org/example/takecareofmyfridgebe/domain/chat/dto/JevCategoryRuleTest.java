package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.CategoryRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 분류 고르기 규칙(기본값 top 0.7, gap 0.15, floor 0.2, widest 4). 확률은 jev-compare 평가 답에서 옮겼다
class JevCategoryRuleTest {

    private static final CategoryRule RULE = CategoryRule.DEFAULT;

    @Test
    @DisplayName("기본 규칙은 top 0.7, gap 0.15, floor 0.2, widest 4다")
    void hasStartingValues() {
        assertThat(RULE).isEqualTo(new CategoryRule(0.7, 0.15, 0.2, 4));
    }

    @Test
    @DisplayName("1등이 0.7 이상이면 1등과 차이가 0.15 이내인 분류만 고른다")
    void picksTopAndClose_whenTopIsHigh() {
        // R01 김치찌개 먹고 싶어
        Map<String, Double> probabilities = probabilities("국", 0.56, "김치", 0.77, "스튜", 0.75, "찌개", 0.97, "수프", 0.38);

        assertThat(RULE.pick(probabilities)).containsExactly("찌개");
    }

    @Test
    @DisplayName("1등과 차이가 꼭 0.15인 분류는 소수 오차가 있어도 고른다")
    void picksCategoryAtExactGap() {
        Map<String, Double> probabilities = probabilities("국", 0.85, "수프", 0.7, "찌개", 0.69);

        assertThat(RULE.pick(probabilities)).containsExactly("국", "수프");
    }

    @Test
    @DisplayName("1등도 0.7 미만인 넓은 요청이면 0.2 이상인 상위 4개를 확률 높은 순으로 고른다")
    void picksTopFour_whenRequestIsBroad() {
        // R19 반찬 추천해줘
        Map<String, Double> probabilities = probabilities("구이", 0.21, "김치", 0.22, "무침", 0.28, "볶음", 0.27, "조림", 0.23,
                "전", 0.21, "회", 0.1);

        assertThat(RULE.pick(probabilities)).containsExactly("무침", "볶음", "조림", "김치");
    }

    @Test
    @DisplayName("넓은 요청에서 0.2 이상인 분류가 4개보다 적으면 그것만 고른다")
    void picksOnlyAboveFloor_whenFewerThanFour() {
        // R12 술안주 추천해줘
        Map<String, Double> probabilities = probabilities("구이", 0.26, "전", 0.23, "튀김", 0.19, "무침", 0.2);

        assertThat(RULE.pick(probabilities)).containsExactly("구이", "전", "무침");
    }

    @Test
    @DisplayName("모든 분류가 0.2 미만이면 빈 목록이다")
    void returnsEmpty_whenAllBelowFloor() {
        Map<String, Double> probabilities = probabilities("국", 0.05, "찌개", 0.19, "디저트", 0.02);

        assertThat(RULE.pick(probabilities)).isEmpty();
        assertThat(RULE.pick(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("확률이 같으면 넘긴 분류 순서를 따른다")
    void keepsGivenOrder_whenProbabilitiesTie() {
        Map<String, Double> probabilities = probabilities("국", 0.4, "수프", 0.4, "찌개", 0.4, "스튜", 0.4, "전골", 0.4);

        assertThat(RULE.pick(probabilities)).containsExactly("국", "수프", "찌개", "스튜");
    }

    private static Map<String, Double> probabilities(Object... pairs) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            probabilities.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return probabilities;
    }
}

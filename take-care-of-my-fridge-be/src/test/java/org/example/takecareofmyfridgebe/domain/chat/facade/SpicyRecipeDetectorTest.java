package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpicyRecipeDetectorTest {

    private static final List<String> SPICY = List.of("청양고추", "홍고추", "청고추", "고춧가루", "고추장", "건고추",
            "베트남고추", "페퍼론치노", "핫소스", "고추기름");

    @Test
    @DisplayName("재료에 고춧가루가 있으면 맛 데이터와 상관없이 매운 레시피다")
    void detectsSpicy_whenRedPepperPowder() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(1L, List.of("배추", "고춧가루")), SPICY)).containsExactly(1L);
    }

    @Test
    @DisplayName("재료 사전 이름이 고추여도 레시피에 적힌 이름이 홍고추면 매운 레시피다")
    void detectsSpicy_whenDisplayNameIsRedChili() {
        // 깐풍기: 사전 이름 "고추", 레시피 원문 "홍고추"
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(366L, List.of("닭고기", "고추", "홍고추")), SPICY))
                .containsExactly(366L);
    }

    @Test
    @DisplayName("그냥 고추(풋고추·꽈리고추 포함)만 있으면 매운 레시피가 아니다")
    void ignoresPlainPepper() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("고추", "고추"), 2L, List.of("꽈리고추"), 3L, List.of("풋고추", "양파")), SPICY)).isEmpty();
    }

    @Test
    @DisplayName("공백을 빼고 이름에 매운 재료가 들어 있으면 잡는다 (다진 청양 고추, 홍고추채)")
    void detectsSpicy_ignoringSpacesAndAffixes() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("다진 청양 고추"), 2L, List.of("홍고추채"), 3L, List.of("간장")), SPICY))
                .containsExactlyInAnyOrder(1L, 2L);
    }
}

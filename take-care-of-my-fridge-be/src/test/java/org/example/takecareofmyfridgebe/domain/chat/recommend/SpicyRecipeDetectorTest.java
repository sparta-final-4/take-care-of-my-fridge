package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpicyRecipeDetectorTest {

    private static final List<String> SPICY = List.of("청양고추", "홍고추", "청고추", "고춧가루", "고추장", "건고추",
            "베트남고추", "페퍼론치노", "핫소스", "고추기름", "김치", "배추김치", "깍두기", "열무김치", "총각김치", "파김치", "갓김치");
    private static final List<String> EXCLUSIONS = List.of("백김치", "물김치", "동치미", "나박김치");
    // application.yml과 같은 목록
    private static final List<String> HOT = List.of("청양고추", "고춧가루", "고추장", "고추기름", "건고추", "베트남고추", "페퍼론치노",
            "핫소스", "김치", "배추김치", "깍두기", "열무김치", "총각김치", "파김치", "갓김치");
    private static final List<String> MILD = List.of("홍고추", "청고추");
    private static final List<String> MILD_EXACT = List.of("고추", "붉은고추", "고추냉이");

    @Test
    @DisplayName("재료에 고춧가루가 있으면 맛 데이터와 상관없이 매운 레시피다")
    void detectsSpicy_whenRedPepperPowder() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(1L, List.of("배추", "고춧가루")), SPICY, EXCLUSIONS)).containsExactly(1L);
    }

    @Test
    @DisplayName("재료 사전 이름이 고추여도 레시피에 적힌 이름이 홍고추면 매운 레시피다")
    void detectsSpicy_whenDisplayNameIsRedChili() {
        // 깐풍기: 사전 이름 "고추", 레시피 원문 "홍고추"
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(366L, List.of("닭고기", "고추", "홍고추")), SPICY, EXCLUSIONS))
                .containsExactly(366L);
    }

    @Test
    @DisplayName("그냥 고추(풋고추·꽈리고추 포함)만 있으면 매운 레시피가 아니다")
    void ignoresPlainPepper() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("고추", "고추"), 2L, List.of("꽈리고추"), 3L, List.of("풋고추", "양파")), SPICY, EXCLUSIONS)).isEmpty();
    }

    @Test
    @DisplayName("공백을 빼고 이름에 매운 재료가 들어 있으면 잡는다 (다진 청양 고추, 홍고추채)")
    void detectsSpicy_ignoringSpacesAndAffixes() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("다진 청양 고추"), 2L, List.of("홍고추채"), 3L, List.of("간장")), SPICY, EXCLUSIONS))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("김치류(김치·배추김치·깍두기 등)가 들어 있으면 매운 레시피다 (두부김치)")
    void detectsSpicy_whenKimchi() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("두부", "김치"), 2L, List.of("배추 김치"), 3L, List.of("깍두기"), 4L, List.of("갓김치")), SPICY, EXCLUSIONS))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("백김치·물김치·동치미·나박김치는 이름에 김치가 들어 있어도 매운 재료로 보지 않는다")
    void ignoresMildKimchi() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("백김치"), 2L, List.of("물 김치"), 3L, List.of("동치미"), 4L, List.of("나박김치", "무"),
                5L, List.of("백김치", "고춧가루")), SPICY, EXCLUSIONS))
                .containsExactly(5L);
    }

    @Test
    @DisplayName("매울 수 있음 정확한 이름 목록이면 고추·붉은 고추·고추냉이는 공백을 빼고 이름이 같아서 매울 수 있는 레시피다")
    void detectsMild_whenExactPepperName() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("고추"), 2L, List.of("붉은 고추"), 3L, List.of("고추냉이"), 4L, List.of("간장")), MILD, MILD_EXACT, EXCLUSIONS))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("매울 수 있음 정확한 이름 목록에 고추가 있어도 오이고추·아삭이고추·꽈리고추·풋고추는 이름이 달라서 매울 수 있는 레시피가 아니다")
    void ignoresPepperVarieties_whenExactPepperName() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("오이고추"), 2L, List.of("아삭이고추"), 3L, List.of("꽈리고추"), 4L, List.of("풋고추")), MILD, MILD_EXACT, EXCLUSIONS))
                .isEmpty();
    }

    @Test
    @DisplayName("고추장·청양고추는 지금처럼 확실히 매운 레시피이고, 그냥 고추는 확실히 매운 레시피가 아니다")
    void keepsHot_whenGochujangOrCheongyang() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(
                1L, List.of("고추장"), 2L, List.of("청양고추"), 3L, List.of("고추")), HOT, EXCLUSIONS))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("재료 이름에 줄바꿈이 있어도 공백처럼 빼고 매운 재료를 잡는다 (청양\n고추)")
    void detectsSpicy_whenIngredientNameHasNewline() {
        assertThat(SpicyRecipeDetector.spicyRecipeIds(Map.of(1L, List.of("청양\n고추")), SPICY, EXCLUSIONS)).containsExactly(1L);
    }
}

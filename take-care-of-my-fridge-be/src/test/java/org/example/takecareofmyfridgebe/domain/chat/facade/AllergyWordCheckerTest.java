package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyWordProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllergyWordCheckerTest {

    // application.yml app.chat.allergy-words와 같은 목록
    public static final ChatAllergyWordProperties WORDS = new ChatAllergyWordProperties(
            List.of("알레르기", "알러지", "못먹", "먹으면안"),
            List.of("에는", "에도", "은", "는", "이", "가", "을", "를", "에", "도"),
            List.of("땅콩호박", "바게트", "스파게티", "멍게살"));

    @Test
    @DisplayName("알레르기·못 먹어·먹으면 안 돼 바로 앞 낱말을 찾는다 (땅콩 알레르기, 새우 못 먹어, 우유 먹으면 안 돼)")
    void findsWordBeforeMarker() {
        assertThat(AllergyWordChecker.mentions("땅콩 알레르기 있어, 뭐 먹지", WORDS)).containsExactly(List.of("땅콩"));
        assertThat(AllergyWordChecker.mentions("땅콩알레르기", WORDS)).containsExactly(List.of("땅콩"));
        assertThat(AllergyWordChecker.mentions("나 새우 못 먹어", WORDS)).containsExactly(List.of("새우"));
        assertThat(AllergyWordChecker.mentions("우유 먹으면 안 돼", WORDS)).containsExactly(List.of("우유"));
        assertThat(AllergyWordChecker.mentions("견과류 알러지가 있어요", WORDS)).containsExactly(List.of("견과류"));
    }

    @Test
    @DisplayName("낱말 그대로를 먼저, 끝 조사를 뗀 것을 다음 후보로 낸다 (새우를 → 새우를·새우, 오이 → 오이·오)")
    void returnsWordThenParticleStripped() {
        assertThat(AllergyWordChecker.mentions("새우를 못 먹어", WORDS)).containsExactly(List.of("새우를", "새우"));
        assertThat(AllergyWordChecker.mentions("견과류에 알레르기", WORDS)).containsExactly(List.of("견과류에", "견과류"));
        assertThat(AllergyWordChecker.mentions("오이 못 먹어", WORDS)).containsExactly(List.of("오이", "오"));
    }

    @Test
    @DisplayName("알레르기를 여러 번 말하면 모두 찾는다")
    void findsAllMentions() {
        assertThat(AllergyWordChecker.mentions("땅콩 알레르기랑 새우 알레르기 있어", WORDS))
                .containsExactly(List.of("땅콩"), List.of("새우"));
    }

    @Test
    @DisplayName("표시 말이 없으면 찾지 않는다 (약불로 끓여, 두부조림 레시피)")
    void returnsEmpty_whenNoMarker() {
        for (String message : List.of("약불로 끓여", "두부조림 레시피", "땅콩 들어간 요리 추천해줘", "")) {
            assertThat(AllergyWordChecker.mentions(message, WORDS)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("\"매운 거 못 먹어\"는 낱말 \"거\"만 내고, 재료인지는 부르는 쪽이 정한다")
    void returnsNonIngredientWord_whenTasteSentence() {
        assertThat(AllergyWordChecker.mentions("매운 거 못 먹어", WORDS)).containsExactly(List.of("거"));
    }
}

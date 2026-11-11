package org.example.takecareofmyfridgebe.domain.chat.safety;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyProperties.Allergy.NotSameKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AllergyWordCheckerTest {

    // config/chat-safety.yml app.chat.safety.allergy와 같은 목록
    public static final ChatSafetyProperties.Allergy WORDS = new ChatSafetyProperties.Allergy(
            List.of("알레르기", "알러지", "못먹", "먹으면안"),
            List.of("에는", "에도", "은", "는", "이", "가", "을", "를", "에", "도"),
            List.of(
                    new NotSameKind("땅콩", List.of("땅콩호박")),
                    new NotSameKind("게", List.of("바게트", "스파게티", "멍게살")),
                    new NotSameKind("게살", List.of("멍게살")),
                    new NotSameKind("우유", List.of("아몬드우유")),
                    new NotSameKind("버터", List.of("땅콩버터")),
                    new NotSameKind("굴", List.of("굴비")),
                    new NotSameKind("김", List.of("김장김치", "김치", "김칫국물", "김칫잎")),
                    new NotSameKind("떡", List.of("떡볶이양념파우더")),
                    new NotSameKind("마", List.of("마늘", "마늘가루", "마늘기름", "마늘종", "마늘즙", "마늘칩", "마늘튀김", "마른오징어",
                            "마스카포네치즈", "마요네즈", "마카다미아")),
                    new NotSameKind("무", List.of("무가당 두유", "무가당 아보카도 마요네즈", "무가당 코코아 파우더", "무가당 코코아가루",
                            "무염버터", "무지개고추", "무화과")),
                    new NotSameKind("묵", List.of("묵은지")),
                    new NotSameKind("물", List.of("물김치국물", "물밤", "물엿", "물전분", "물파래")),
                    new NotSameKind("밤", List.of("밤고구마")),
                    new NotSameKind("배", List.of("배추", "배추김치")),
                    new NotSameKind("쑥", List.of("쑥갓")),
                    new NotSameKind("양", List.of("양겨자", "양념간장", "양념장", "양념족발", "양배추", "양상추", "양송이버섯",
                            "양송이버섯 밑동", "양장피 면", "양파", "양파 껍질", "양파가루", "양파즙")),
                    new NotSameKind("조", List.of("조개", "조개살", "조기", "조랭이떡", "조림간장", "조미료", "조선부추", "조청"))));

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

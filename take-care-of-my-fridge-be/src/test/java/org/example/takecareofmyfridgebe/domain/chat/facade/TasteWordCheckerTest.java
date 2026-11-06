package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.facade.TasteWordChecker.Stance;
import org.example.takecareofmyfridgebe.domain.chat.facade.TasteWordChecker.TasteStance;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TasteWordCheckerTest {

    static final ChatTasteWordProperties WORDS = new ChatTasteWordProperties(
            List.of("매운", "맵게", "맵지", "매콤", "얼큰", "칼칼"), List.of("달달", "달게", "단거", "단걸", "달콤", "단맛"),
            List.of("안", "덜"), List.of("말고", "빼고", "않", "싫", "없이"),
            List.of("짠", "짭짤", "짜게", "짭조름"), List.of("감칠", "고소"), List.of("순한", "담백", "슴슴", "심심", "자극적이지"));

    @Test
    @DisplayName("매운맛 단어만 있으면 원하는 맛이다 (좀 더 매운 걸로, 매운 거 먹고 싶어, 칼칼한 거)")
    void wantsSpicy_whenNoNegation() {
        assertThat(TasteWordChecker.check("좀 더 매운 걸로", WORDS)).containsExactly(new TasteStance(Taste.SPICY, Stance.WANT));
        assertThat(TasteWordChecker.check("매운 거 먹고 싶어", WORDS)).containsExactly(new TasteStance(Taste.SPICY, Stance.WANT));
        assertThat(TasteWordChecker.check("칼칼한 거", WORDS)).containsExactly(new TasteStance(Taste.SPICY, Stance.WANT));
    }

    @Test
    @DisplayName("매운맛 단어 뒤에 말고·않·싫이 오거나 앞에 안·덜이 붙으면 피할 맛이다")
    void avoidsSpicy_whenNegated() {
        for (String message : List.of("매운 거 말고", "안 맵게", "덜 맵게", "맵지 않게", "매운 건 싫어", "안매운 걸로")) {
            assertThat(TasteWordChecker.check(message, WORDS)).as(message)
                    .containsExactly(new TasteStance(Taste.SPICY, Stance.AVOID));
        }
    }

    @Test
    @DisplayName("부정어가 맛 단어와 떨어져 있으면 원하는 맛이다 (30분 안에 매운 거, 짭짤한 거 말고 매운 걸로)")
    void wantsSpicy_whenNegationIsElsewhere() {
        assertThat(TasteWordChecker.check("30분 안에 매운 거", WORDS)).containsExactly(new TasteStance(Taste.SPICY, Stance.WANT));
        assertThat(TasteWordChecker.check("짭짤한 거 말고 매운 걸로", WORDS)).containsExactly(new TasteStance(Taste.SPICY, Stance.WANT));
    }

    @Test
    @DisplayName("단맛도 같은 방식이고 공백을 빼고 본다 (단 거 싫어 → 피함, 달달한 거 → 원함), 맛 단어가 없으면 빈 목록")
    void checksSweetAndIgnoresMessagesWithoutTasteWords() {
        assertThat(TasteWordChecker.check("단 거 싫어", WORDS)).containsExactly(new TasteStance(Taste.SWEET, Stance.AVOID));
        assertThat(TasteWordChecker.check("달달한 거", WORDS)).containsExactly(new TasteStance(Taste.SWEET, Stance.WANT));
        assertThat(TasteWordChecker.check("매운 거 말고 달게", WORDS)).containsExactly(
                new TasteStance(Taste.SPICY, Stance.AVOID), new TasteStance(Taste.SWEET, Stance.WANT));
        assertThat(TasteWordChecker.check("다른 거 없어?", WORDS)).isEmpty();
    }

    @Test
    @DisplayName("이번 말에 단어가 나온 맛을 원하든 피하든 모두 찾는다 (매운 거 말고 → 매운맛, 짭짤하게 → 짠맛, 김치찌개 → 없음)")
    void findsMentionedTastes() {
        assertThat(TasteWordChecker.mentioned("매운 거 말고", WORDS)).containsExactly(Taste.SPICY);
        assertThat(TasteWordChecker.mentioned("짭짤하고 고소한 거", WORDS)).containsExactlyInAnyOrder(Taste.SALTY, Taste.UMAMI);
        assertThat(TasteWordChecker.mentioned("담백한 거", WORDS)).containsExactly(Taste.MILD);
        assertThat(TasteWordChecker.mentioned("김치찌개", WORDS)).isEmpty();
    }
}

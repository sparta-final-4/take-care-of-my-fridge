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
            List.of("매운", "맵게", "맵지", "매콤", "얼큰", "칼칼"), List.of("달달", "달게", "단거", "단걸", "단것", "달콤", "단맛"),
            List.of("안", "덜"), List.of("말고", "빼고", "않", "싫", "없이", "못먹", "빼줘", "빼주", "빼달", "제외", "안들어간", "없는"),
            List.of("짠", "짭짤", "짜게", "짭조름"), List.of("감칠", "고소"), List.of("순한", "담백", "슴슴", "심심", "자극적이지"),
            List.of("더"), List.of("걸로", "거로", "것으로", "먹고싶", "땡", "좋아", "원해", "당겨"),
            List.of("맛상관없", "맛은상관없", "맛아무거나", "맛은아무거나"));

    @Test
    @DisplayName("맛 상관없이·맛은 아무거나는 맛 조건을 지우라는 말이고, 매운 건 빼줘는 아니다")
    void returnsClearsTastes_whenClearWord() {
        for (String message : List.of("맛 상관없이", "맛은 상관없어", "맛은 아무거나", "맛 아무거나 다른 거")) {
            assertThat(TasteWordChecker.clearsTastes(message, WORDS)).as(message).isTrue();
        }
        assertThat(TasteWordChecker.clearsTastes("매운 건 빼줘", WORDS)).isFalse();
    }

    @Test
    @DisplayName("빼 달라·제외·안 들어간·없는 말이 맛 단어 뒤에 오면 피할 맛이다 (공백 무시)")
    void avoidsTaste_whenRemoveWord() {
        for (String message : List.of("매운 건 빼줘", "매운 건 빼 줘", "매운 거 빼주세요", "매운 거 빼달라고", "매운 거 제외하고 추천해줘",
                "매운 게 안 들어간 거", "매운 거 없는 걸로", "김피탕 같은 메뉴에서 매운 건 빼줘")) {
            assertThat(TasteWordChecker.check(message, WORDS)).as(message)
                    .containsExactly(new TasteStance(Taste.SPICY, Stance.AVOID));
        }
        assertThat(TasteWordChecker.check("단 거 빼주세요", WORDS)).containsExactly(new TasteStance(Taste.SWEET, Stance.AVOID));
        assertThat(TasteWordChecker.stances("짠 거 제외", WORDS)).containsExactly(new TasteStance(Taste.SALTY, Stance.AVOID));
    }

    @Test
    @DisplayName("원하는 말이 붙어야 확실히 원하는 맛이고, 맛 단어만 있거나 원하는 말 앞에 안이 끼면 확실하지 않다")
    void returnsSurelyWanted_onlyWhenWantWordAttached() {
        for (String message : List.of("좀 더 매운 걸로", "더 매콤하게", "매운 거 먹고 싶어", "매운 거로", "얼큰한 게 땡겨")) {
            assertThat(TasteWordChecker.surelyWanted(message, Taste.SPICY, WORDS)).as(message).isTrue();
        }
        for (String message : List.of("매운 건 별로야", "칼칼한 거", "매운 거 안 먹고 싶어", "매운 건 빼줘", "김치찌개")) {
            assertThat(TasteWordChecker.surelyWanted(message, Taste.SPICY, WORDS)).as(message).isFalse();
        }
    }

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

    @Test
    @DisplayName("\"매운 거 못 먹어\"는 맛 단어 뒤에 \"못 먹\"이 있어 매운맛을 피하는 말이다")
    void returnsAvoidSpicy_whenCannotEatSpicy() {
        assertThat(TasteWordChecker.check("매운 거 못 먹어", WORDS)).containsExactly(new TasteStance(Taste.SPICY, Stance.AVOID));
    }

    @Test
    @DisplayName("stances는 맛 전체의 원함·피함을 같은 부정어 규칙으로 본다 (담백한 거로 → 순한맛 원함, 짠 거 말고 → 짠맛 피함, 단 것도 → 단맛 원함)")
    void returnsStancesForAllTastes() {
        assertThat(TasteWordChecker.stances("담백한 거로", WORDS)).containsExactly(new TasteStance(Taste.MILD, Stance.WANT));
        assertThat(TasteWordChecker.stances("짠 거 말고", WORDS)).containsExactly(new TasteStance(Taste.SALTY, Stance.AVOID));
        assertThat(TasteWordChecker.stances("단 것도", WORDS)).containsExactly(new TasteStance(Taste.SWEET, Stance.WANT));
        assertThat(TasteWordChecker.stances("다른 거", WORDS)).isEmpty();
    }
}

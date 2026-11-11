package org.example.takecareofmyfridgebe.domain.chat.safety;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatHealthWordProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthWordCheckerTest {

    // config/chat-safety.yml app.chat.health-words와 같은 목록
    public static final ChatHealthWordProperties WORDS = new ChatHealthWordProperties(
            List.of("당뇨", "혈당", "고혈압", "혈압", "고지혈", "콜레스테롤", "신장질환", "신장병", "신장기능", "콩팥", "투석",
                    "통풍", "요산", "질환", "환자", "위염", "역류성", "항암", "알레르기", "알러지", "아토피", "임신", "임산부", "산모",
                    "수유", "모유", "복용", "처방", "약먹", "약을먹", "약드시"),
            List.of("약불", "약간", "약한불", "신장개업", "수유역"));

    @Test
    @DisplayName("질환·알레르기·임신·약을 말하면 건강 관련 말이다 (당뇨에 좋은 요리, 임신 중에 회 먹어도 돼?)")
    void returnsTrue_whenHealthWords() {
        for (String message : List.of("당뇨에 좋은 요리", "임신 중에 회 먹어도 돼?", "땅콩 알레르기 있어", "고혈압인데 짠 거 괜찮아?",
                "감기약 먹고 있는데 술안주", "통풍 있으면 뭐 먹어", "신장 기능이 안 좋은데 국 추천", "콩팥에 좋은 음식",
                "투석 중인데 먹어도 돼?", "수유 중에 커피 괜찮아?", "혈압약 복용 중이야", "약 먹고 먹어도 되는 음식")) {
            assertThat(HealthWordChecker.mentions(message, WORDS)).as(message).isTrue();
        }
    }

    @Test
    @DisplayName("요리 말·키·다이어트는 건강 관련 말이 아니다 (약불로 끓여, 약 10분, 신장 170cm, 신장개업, 다이어트 메뉴)")
    void returnsFalse_whenCookingWords() {
        for (String message : List.of("약불로 끓여", "약간 매운 거", "약 10분 끓여", "약한 불에 볶아", "약 2.5kg 고기",
                "신장개업 메뉴", "신장 170cm인데 단백질 많은 음식", "다이어트 메뉴 추천해줘", "수유역 맛집 메뉴", "두부조림 레시피")) {
            assertThat(HealthWordChecker.mentions(message, WORDS)).as(message).isFalse();
        }
    }

    @Test
    @DisplayName("제외 말을 지운 뒤에도 건강 말이 남으면 건강 관련 말이다 (약간 매운데 당뇨 있어)")
    void returnsTrue_whenHealthWordRemainsAfterExclusion() {
        assertThat(HealthWordChecker.mentions("약간 매운 걸로, 당뇨 있어", WORDS)).isTrue();
    }

    @Test
    @DisplayName("빈 말이나 null은 건강 관련 말이 아니다")
    void returnsFalse_whenBlank() {
        assertThat(HealthWordChecker.mentions(null, WORDS)).isFalse();
        assertThat(HealthWordChecker.mentions("  ", WORDS)).isFalse();
    }
}

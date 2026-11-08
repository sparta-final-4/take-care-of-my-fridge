package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatFridgeShopWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FridgeShopWordCheckerTest {

    // config/chat-words.yml의 app.chat.fridge-shop-words와 같은 값
    static final ChatFridgeShopWordProperties WORDS = new ChatFridgeShopWordProperties(
            List.of("남았", "아직있", "냉장고에뭐", "냉장고에있", "냉장고에서", "버려야", "유통기한지난", "소비기한지난"),
            List.of("버려야", "지난", "임박", "유통기한", "소비기한"),
            List.of("추천", "만들", "해먹", "요리", "레시피", "뭐먹", "먹지", "먹을까"));

    private static Optional<ChatIntent> check(String message) {
        return FridgeShopWordChecker.check(message, WORDS, AskWordCheckerTest.WORDS);
    }

    @Test
    @DisplayName("냉장고 보기 말이 있으면 냉장고 보기로 본다")
    void returnsFridge_whenFridgeWord() {
        for (String message : List.of("냉장고에 뭐 있어", "밀키트 뭐 남았어", "두부 아직 있어?", "곧 버려야 할 거 있어?",
                "내 냉장고에서 밀키트 어떤 거 있어?", "냉장고에 먹을 거 뭐 남았어")) {
            assertThat(check(message)).as(message).contains(ChatIntent.FRIDGE);
        }
    }

    @Test
    @DisplayName("요리 말이 같이 있으면 냉장고 보기로 보지 않는다 (냉장고에 있는 재료로 뭐 먹지, 두부 있으면 두부 요리 추천해줘)")
    void returnsEmpty_whenCookWord() {
        for (String message : List.of("냉장고에 있는 재료로 뭐 먹지", "냉장고에 있는 재료로 뭐 해 먹을까", "두부 있으면 두부 요리 추천해줘")) {
            assertThat(check(message)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("묻는 말이 있으면 냉장고 보기로 보지 않는다 (냉장고에서 꺼낸 고기 어떻게 해동해?, 유통기한 지난 거 먹어도 돼?)")
    void returnsEmpty_whenAsking() {
        for (String message : List.of("냉장고에서 꺼낸 고기 어떻게 해동해?", "유통기한 지난 거 먹어도 돼?", "유통기한 지난 우유 괜찮아?")) {
            assertThat(check(message)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("냉장고 보기 말이 없으면 바꾸지 않는다 (주문한 거 언제 와?, 밀키트 뭐 있어)")
    void returnsEmpty_whenNoFridgeWord() {
        for (String message : List.of("주문한 거 언제 와?", "밀키트 뭐 있어", "안녕", "")) {
            assertThat(check(message)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("범위는 밀키트 단어면 MEALKIT, 버려야·지난 말이면 IMMINENT, 그 밖은 ALL")
    void picksScopeByWords() {
        assertThat(FridgeShopWordChecker.scope("밀키트 뭐 남았어", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.MEALKIT);
        assertThat(FridgeShopWordChecker.scope("곧 버려야 할 거 있어?", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.IMMINENT);
        assertThat(FridgeShopWordChecker.scope("유통기한 지난 거 있어?", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.IMMINENT);
        assertThat(FridgeShopWordChecker.scope("냉장고에 뭐 있어", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.ALL);
    }
}

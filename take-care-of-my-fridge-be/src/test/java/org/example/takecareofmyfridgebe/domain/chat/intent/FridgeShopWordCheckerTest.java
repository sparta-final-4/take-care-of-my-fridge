package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class FridgeShopWordCheckerTest {

    // config/chat-words.yml의 app.chat.words.fridge-shop과 같은 값
    public static final ChatWordProperties.FridgeShop WORDS = new ChatWordProperties.FridgeShop(
            List.of("남았", "아직있", "냉장고에뭐", "냉장고에있", "냉장고에서", "버려야", "유통기한지난", "소비기한지난", "처리해야할거", "처리할거"),
            List.of("버려야", "지난", "임박", "유통기한", "소비기한", "상한", "상할", "상했", "오래된", "오래됐", "처리해야"),
            List.of("기본양념", "조미료"),
            List.of("사고싶", "어디서사", "장바구니", "담아", "주문해"),
            List.of("세일", "할인", "특가", "번개"),
            List.of("추천", "만들", "해먹", "요리", "레시피", "뭐먹", "먹지", "먹을까"),
            List.of("어떻게", "방법", "보관", "해동", "먹어도돼", "괜찮", "왜", "언제와", "언제도착", "배송", "짜줘", "짜줄"));

    private static Optional<ChatIntent> check(String message) {
        return FridgeShopWordChecker.check(message, WORDS, AskWordCheckerTest.WORDS);
    }

    @Test
    @DisplayName("냉장고 보기 말이 있으면 냉장고 보기로 본다")
    void returnsFridge_whenFridgeWord() {
        for (String message : List.of("냉장고에 뭐 있어", "밀키트 뭐 남았어", "두부 아직 있어?", "곧 버려야 할 거 있어?",
                "내 냉장고에서 밀키트 어떤 거 있어?", "냉장고에 먹을 거 뭐 남았어", "슬슬 처리해야 할 거 있어?")) {
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
    @DisplayName("짜 달라는 말이나 묻는 말이 있으면 일반 답으로 보고, 얼마나·가격만 있으면 일반 답으로 보지 않는다")
    void returnsAsksGeneral_whenPlanOrQuestionWord() {
        for (String message : List.of("장보기 목록 짜줘", "일주일 장보기 목록 짜 줄래?", "주문한 거 언제 와?")) {
            assertThat(FridgeShopWordChecker.asksGeneral(message, WORDS)).as(message).isTrue();
        }
        for (String message : List.of("두부 얼마나 남았어?", "두부 가격 얼마야?", "당근 사고 싶어")) {
            assertThat(FridgeShopWordChecker.asksGeneral(message, WORDS)).as(message).isFalse();
        }
    }

    @Test
    @DisplayName("냉장고 보기 말이 없으면 바꾸지 않는다 (주문한 거 언제 와?, 밀키트 뭐 있어, 고기 핏물 처리해야 돼?)")
    void returnsEmpty_whenNoFridgeWord() {
        for (String message : List.of("주문한 거 언제 와?", "밀키트 뭐 있어", "안녕", "", "고기 핏물 처리해야 돼?")) {
            assertThat(check(message)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("냉장고 보기 말이 없고 사기 말이 있으면 사기로 본다")
    void returnsShop_whenShopWord() {
        for (String message : List.of("당근 사고 싶어", "두부 어디서 사", "대파 장바구니에 담아줘", "우유 주문해줘")) {
            assertThat(check(message)).as(message).contains(ChatIntent.SHOP);
        }
    }

    @Test
    @DisplayName("할인 말이 있으면 사기로 보고 할인 질문으로 표시한다")
    void returnsShop_whenSaleWord() {
        for (String message : List.of("지금 세일하는 재료 있어?", "할인하는 밀키트 뭐 있어?", "특가 상품 보여줘", "두부 세일해?")) {
            assertThat(check(message)).as(message).contains(ChatIntent.SHOP);
            assertThat(FridgeShopWordChecker.asksSale(message, WORDS)).as(message).isTrue();
        }
        assertThat(FridgeShopWordChecker.asksSale("당근 사고 싶어", WORDS)).isFalse();
    }

    @Test
    @DisplayName("냉장고를 말하지 않아도 기본 양념·조미료를 물으면 냉장고 보기, 사기·할인 말이 있으면 사기, 요리 말이 있으면 바꾸지 않는다")
    void returnsFridge_whenSeasoningWordOnly() {
        assertThat(check("조미료 뭐있어?")).contains(ChatIntent.FRIDGE);
        assertThat(check("기본 양념 뭐 남았지")).contains(ChatIntent.FRIDGE);
        assertThat(check("조미료 세일해?")).contains(ChatIntent.SHOP);
        assertThat(check("기본 양념으로 만들 요리 추천해줘")).isEmpty();
        assertThat(check("양념 뭐 있어?")).isEmpty();
    }

    @Test
    @DisplayName("냉장고 보기 말과 사기 말이 같이 있으면 냉장고 보기로 본다")
    void returnsFridge_whenFridgeAndShopWords() {
        assertThat(check("두부 아직 있어? 없으면 장바구니에 담아줘")).contains(ChatIntent.FRIDGE);
    }

    @Test
    @DisplayName("주문 상태를 묻거나, 묻는 말·요리 말이 같이 있으면 사기로 보지 않는다")
    void returnsEmpty_whenShopWordIsMisleading() {
        for (String message : List.of("주문한 거 언제 와?", "남은 국은 밀폐용기에 담아 보관해?", "두부 사서 만들 요리 추천해줘")) {
            assertThat(check(message)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("범위는 양념 말이면 SEASONING, 밀키트 단어면 MEALKIT, 버려야·지난 말이면 IMMINENT, 그 밖은 ALL")
    void picksScopeByWords() {
        assertThat(FridgeShopWordChecker.scope("밀키트 뭐 남았어", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.MEALKIT);
        assertThat(FridgeShopWordChecker.scope("곧 버려야 할 거 있어?", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.IMMINENT);
        assertThat(FridgeShopWordChecker.scope("유통기한 지난 거 있어?", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.IMMINENT);
        assertThat(FridgeShopWordChecker.scope("냉장고에 뭐 있어", WORDS, MealkitWordCheckerTest.WORDS)).isEqualTo(FridgeScope.ALL);
        assertThat(FridgeShopWordChecker.scope("내 냉장고에 기본 양념 뭐있어?", WORDS, MealkitWordCheckerTest.WORDS))
                .isEqualTo(FridgeScope.SEASONING);
        assertThat(FridgeShopWordChecker.seasoningWord("기본 양념", WORDS)).isTrue();
        assertThat(FridgeShopWordChecker.seasoningWord("두부", WORDS)).isFalse();
        // "양념"만으로는 기본 양념 보기로 보지 않는다
        assertThat(FridgeShopWordChecker.scope("양념치킨 밀키트 남았어?", WORDS, MealkitWordCheckerTest.WORDS))
                .isEqualTo(FridgeScope.MEALKIT);
        assertThat(FridgeShopWordChecker.scope("냉장고에 조미료 뭐 있어?", WORDS, MealkitWordCheckerTest.WORDS))
                .isEqualTo(FridgeScope.SEASONING);
    }
}

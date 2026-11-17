package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FridgeShopWordCheckerTest {

    // config/chat-words.yml의 app.chat.words.fridge-shop과 같은 값
    public static final ChatWordProperties.FridgeShop WORDS = new ChatWordProperties.FridgeShop(
            List.of("버려야", "지난", "임박", "유통기한", "소비기한", "상한", "상할", "상했", "오래된", "오래됐", "처리해야"),
            List.of("기본양념", "조미료"),
            List.of("세일", "할인", "특가", "번개"));

    @Test
    @DisplayName("할인 말이 있으면 할인 질문으로 표시하고, 사기 말만 있으면 아니다")
    void returnsSale_whenSaleWord() {
        for (String message : List.of("지금 세일하는 재료 있어?", "할인하는 밀키트 뭐 있어?", "특가 상품 보여줘", "두부 세일해?")) {
            assertThat(FridgeShopWordChecker.asksSale(message, WORDS)).as(message).isTrue();
        }
        assertThat(FridgeShopWordChecker.asksSale("당근 사고 싶어", WORDS)).isFalse();
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

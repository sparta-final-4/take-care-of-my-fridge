package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;

import java.util.List;

/**
 * 냉장고 보기·사기에서 볼 것을 메시지의 단어로 정한다. 스프링에 의존하지 않는 순수 클래스. 공백은 빼고 비교한다.
 * 의도(냉장고 보기·사기)는 Jev·OpenAI가 정하고, 여기서는 냉장고 보기 범위·할인 여부·양념 말만 본다 (ChatIntentFacade.lookupOf).
 */
public final class FridgeShopWordChecker {

    private FridgeShopWordChecker() {
    }

    // 할인 말("세일·할인·특가·번개")이 있는지. 있으면 사기에서 할인 중인 상품만 보여 준다
    public static boolean asksSale(String message, ChatWordProperties.FridgeShop words) {
        return containsAny(ChatTexts.removeWhitespace(message), words.sale());
    }

    // 냉장고 보기 범위: 양념 말("기본 양념·조미료") → SEASONING, 밀키트 단어 → MEALKIT, 임박 말("버려야·지난·임박") → IMMINENT, 그 밖은 ALL
    public static FridgeScope scope(String message, ChatWordProperties.FridgeShop words, ChatWordProperties.Mealkit mealkitWords) {
        String text = ChatTexts.removeWhitespace(message);
        if (containsAny(text, words.seasoning())) {
            return FridgeScope.SEASONING;
        }
        if (containsAny(text, mealkitWords.mealkit())) {
            return FridgeScope.MEALKIT;
        }
        if (containsAny(text, words.imminent())) {
            return FridgeScope.IMMINENT;
        }
        return FridgeScope.ALL;
    }

    // 양념 말("기본 양념", "조미료")이 들어간 이름인지. 냉장고 보기에서 찾을 재료 이름으로 보지 않는다
    public static boolean seasoningWord(String name, ChatWordProperties.FridgeShop words) {
        return containsAny(ChatTexts.removeWhitespace(name), words.seasoning());
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(ChatTexts::removeWhitespace).anyMatch(word -> !word.isEmpty() && text.contains(word));
    }
}

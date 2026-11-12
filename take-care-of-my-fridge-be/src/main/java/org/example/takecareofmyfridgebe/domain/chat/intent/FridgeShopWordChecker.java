package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;

import java.util.List;
import java.util.Optional;

/**
 * 메시지의 단어로 냉장고 보기(FRIDGE)·사기(SHOP)인지 본다. 스프링에 의존하지 않는 순수 클래스. 공백은 빼고 비교한다.
 * <ul>
 *   <li>묻는 말(AskWordChecker)이 있으면 바꾸지 않는다 ("냉장고에서 꺼낸 고기 어떻게 해동해?", "유통기한 지난 거 먹어도 돼?")</li>
 *   <li>요리 말이 있으면 바꾸지 않는다 ("냉장고에 있는 재료로 뭐 먹지", "두부 있으면 두부 요리 추천해줘")</li>
 *   <li>냉장고 보기 말이 있으면 FRIDGE, 없고 사기 말("사고 싶·어디서 사·장바구니·담아·주문해")이나 할인 말("세일·할인·특가·번개")이 있으면 SHOP,
 *       그것도 없고 양념 말("기본 양념·조미료")이 있으면 FRIDGE ("조미료 뭐 있어?")</li>
 * </ul>
 */
public final class FridgeShopWordChecker {

    private FridgeShopWordChecker() {
    }

    // 서버가 바꿀 의도. 바꾸지 않으면 빈 값
    public static Optional<ChatIntent> check(String message, ChatWordProperties.FridgeShop words, ChatWordProperties.Ask askWords) {
        String text = ChatTexts.removeWhitespace(message);
        if (text.isEmpty() || AskWordChecker.asks(message, askWords) || cooks(message, words)) {
            return Optional.empty();
        }
        // 냉장고 보기 말과 사기 말이 같이 있으면 냉장고 보기 (없는 재료면 상품 카드가 같이 붙는다)
        if (containsAny(text, words.fridge())) {
            return Optional.of(ChatIntent.FRIDGE);
        }
        if (containsAny(text, words.shop()) || containsAny(text, words.sale())) {
            return Optional.of(ChatIntent.SHOP);
        }
        // "조미료 뭐 있어?"처럼 냉장고를 말하지 않아도 기본 양념을 물으면 냉장고 보기 (사기·할인 말이 있으면 위에서 사기)
        if (containsAny(text, words.seasoning())) {
            return Optional.of(ChatIntent.FRIDGE);
        }
        return Optional.empty();
    }

    // 할인 말("세일·할인·특가·번개")이 있는지. 있으면 사기에서 할인 중인 상품만 보여 준다
    public static boolean asksSale(String message, ChatWordProperties.FridgeShop words) {
        return containsAny(ChatTexts.removeWhitespace(message), words.sale());
    }

    // 요리 말("추천·만들·해먹·뭐 먹지")이 있는지. 있으면 냉장고 말이 있어도 추천으로 둔다
    public static boolean cooks(String message, ChatWordProperties.FridgeShop words) {
        return containsAny(ChatTexts.removeWhitespace(message), words.cook());
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

    // 냉장고 보기·사기가 아니라 일반 답으로 볼 묻는 말이 있는지 ("유통기한 지난 거 먹어도 돼?", "주문한 거 언제 와?")
    public static boolean asksGeneral(String message, ChatWordProperties.FridgeShop words) {
        return message != null && containsAny(ChatTexts.removeWhitespace(message), words.general());
    }

    // 양념 말("기본 양념", "조미료")이 들어간 이름인지. 냉장고 보기에서 찾을 재료 이름으로 보지 않는다
    public static boolean seasoningWord(String name, ChatWordProperties.FridgeShop words) {
        return containsAny(ChatTexts.removeWhitespace(name), words.seasoning());
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(ChatTexts::removeWhitespace).anyMatch(word -> !word.isEmpty() && text.contains(word));
    }
}

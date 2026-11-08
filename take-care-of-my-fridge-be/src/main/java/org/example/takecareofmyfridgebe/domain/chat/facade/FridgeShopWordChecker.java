package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAskWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatFridgeShopWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatMealkitWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;

import java.util.List;
import java.util.Optional;

/**
 * 메시지의 단어로 냉장고 보기(FRIDGE)인지 본다. 스프링에 의존하지 않는 순수 클래스. 공백은 빼고 비교한다.
 * <ul>
 *   <li>묻는 말(AskWordChecker)이 있으면 바꾸지 않는다 ("냉장고에서 꺼낸 고기 어떻게 해동해?", "유통기한 지난 거 먹어도 돼?")</li>
 *   <li>요리 말이 있으면 바꾸지 않는다 ("냉장고에 있는 재료로 뭐 먹지", "두부 있으면 두부 요리 추천해줘")</li>
 *   <li>냉장고 보기 말이 있으면 FRIDGE</li>
 * </ul>
 */
public final class FridgeShopWordChecker {

    private FridgeShopWordChecker() {
    }

    // 서버가 바꿀 의도. 바꾸지 않으면 빈 값
    public static Optional<ChatIntent> check(String message, ChatFridgeShopWordProperties words, ChatAskWordProperties askWords) {
        String text = compact(message);
        if (text.isEmpty() || AskWordChecker.asks(message, askWords) || cooks(message, words)) {
            return Optional.empty();
        }
        if (containsAny(text, words.fridge())) {
            return Optional.of(ChatIntent.FRIDGE);
        }
        return Optional.empty();
    }

    // 요리 말("추천·만들·해먹·뭐 먹지")이 있는지. 있으면 냉장고 말이 있어도 추천으로 둔다
    public static boolean cooks(String message, ChatFridgeShopWordProperties words) {
        return containsAny(compact(message), words.cook());
    }

    // 냉장고 보기 범위: 밀키트 단어 → MEALKIT, 임박 말("버려야·지난·임박") → IMMINENT, 그 밖은 ALL
    public static FridgeScope scope(String message, ChatFridgeShopWordProperties words, ChatMealkitWordProperties mealkitWords) {
        String text = compact(message);
        if (containsAny(text, mealkitWords.mealkit())) {
            return FridgeScope.MEALKIT;
        }
        if (containsAny(text, words.imminent())) {
            return FridgeScope.IMMINENT;
        }
        return FridgeScope.ALL;
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(FridgeShopWordChecker::compact).anyMatch(word -> !word.isEmpty() && text.contains(word));
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}

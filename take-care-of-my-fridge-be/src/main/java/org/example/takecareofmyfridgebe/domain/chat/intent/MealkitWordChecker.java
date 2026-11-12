package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;

import java.util.List;

/**
 * 메시지로 밀키트를 보여 줄지 정한다. 스프링에 의존하지 않는 순수 클래스. 의도 프롬프트는 바꾸지 않고 서버가 말만 본다.
 * <ul>
 *   <li>밀키트 단어("밀키트")가 부정 없이 있으면 밀키트만 ({@link Mode#ONLY})</li>
 *   <li>그게 아니고 번거로워하는 말("간단·간편·빨리·귀찮·요리하기 싫")이 부정 없이 있으면 레시피 카드 + 밀키트 한 장 ({@link Mode#WITH_RECIPES})</li>
 *   <li>단어가 부정과 붙어 있으면("밀키트 말고", "안 귀찮") 그 단어는 원하지 않는 것이다. 밀키트 단어가 부정이면 다른 말이 있어도 밀키트를 보여 주지 않는다</li>
 *   <li>부정 위치는 맛 단어 확인(TasteWordChecker)과 같다: "안·덜"은 단어 바로 앞, "말고·빼고·않·싫·없이"는 단어 뒤 몇 글자 안</li>
 * </ul>
 */
public final class MealkitWordChecker {

    private MealkitWordChecker() {
    }

    public enum Mode {
        // 밀키트를 보여 주지 않는다
        NONE,
        // 레시피 카드 아래 밀키트 한 장
        WITH_RECIPES,
        // 밀키트만 (레시피 카드 없음)
        ONLY;

        public boolean shows() {
            return this != NONE;
        }
    }

    public static Mode check(String message, ChatWordProperties.Mealkit words, ChatWordProperties.Taste negations) {
        String text = ChatTexts.removeWhitespace(message);
        Stance mealkit = stance(text, words.mealkit(), negations);
        if (mealkit == Stance.AVOID) {
            return Mode.NONE;
        }
        if (mealkit == Stance.WANT) {
            return Mode.ONLY;
        }
        return stance(text, words.easy(), negations) == Stance.WANT ? Mode.WITH_RECIPES : Mode.NONE;
    }

    private enum Stance { NONE, WANT, AVOID }

    // 단어가 하나라도 부정과 붙어 있으면 AVOID, 단어만 있으면 WANT
    private static Stance stance(String text, List<String> targets, ChatWordProperties.Taste negations) {
        boolean found = false;
        for (String target : targets) {
            String word = ChatTexts.removeWhitespace(target);
            if (word.isEmpty()) {
                continue;
            }
            int at = text.indexOf(word);
            while (at >= 0) {
                found = true;
                if (TasteWordChecker.negatedBefore(text.substring(0, at), negations)
                        || TasteWordChecker.negatedAfter(text, at + word.length(), negations)) {
                    return Stance.AVOID;
                }
                at = text.indexOf(word, at + 1);
            }
        }
        return found ? Stance.WANT : Stance.NONE;
    }
}

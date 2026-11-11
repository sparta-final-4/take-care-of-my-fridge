package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.intent.MealkitWordChecker.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MealkitWordCheckerTest {

    public static final ChatWordProperties.Mealkit WORDS = new ChatWordProperties.Mealkit(
            List.of("밀키트"), List.of("간단", "간편", "빨리", "귀찮", "요리하기싫"));

    private static Mode check(String message) {
        return MealkitWordChecker.check(message, WORDS, TasteWordCheckerTest.WORDS);
    }

    @Test
    @DisplayName("밀키트를 부정 없이 말하면 밀키트만 보여 준다 (밀키트 추천해줘, 밀키트 뭐 있어, 국물 밀키트)")
    void returnsOnly_whenMealkitWordWithoutNegation() {
        for (String message : List.of("밀키트 추천해줘", "밀키트 뭐 있어", "국물 밀키트", "간단하게 밀키트로")) {
            assertThat(check(message)).as(message).isEqualTo(Mode.ONLY);
        }
    }

    @Test
    @DisplayName("번거로워하는 말만 있으면 레시피 카드와 밀키트 한 장을 보여 준다")
    void returnsWithRecipes_whenOnlyEasyWord() {
        for (String message : List.of("간단하게 먹을 거", "간편한 저녁", "빨리 되는 국", "귀찮은데 뭐 먹지", "요리하기 싫어", "간단한 반찬")) {
            assertThat(check(message)).as(message).isEqualTo(Mode.WITH_RECIPES);
        }
    }

    @Test
    @DisplayName("밀키트 단어가 부정과 붙어 있으면 다른 말이 있어도 밀키트를 보여 주지 않는다")
    void returnsNone_whenMealkitNegated() {
        for (String message : List.of("밀키트 말고 레시피", "밀키트는 싫어", "밀키트 빼고 간단하게", "밀키트 없이")) {
            assertThat(check(message)).as(message).isEqualTo(Mode.NONE);
        }
    }

    @Test
    @DisplayName("번거로워하는 말이 부정이거나 밀키트 단어가 없으면 밀키트를 보여 주지 않는다")
    void returnsNone_whenEasyWordNegatedOrMissing() {
        for (String message : List.of("안 귀찮아", "간단한 거 말고 제대로", "두부로 뭐 해 먹지", "", "매운 거")) {
            assertThat(check(message)).as(message).isEqualTo(Mode.NONE);
        }
        assertThat(check(null)).isEqualTo(Mode.NONE);
    }
}

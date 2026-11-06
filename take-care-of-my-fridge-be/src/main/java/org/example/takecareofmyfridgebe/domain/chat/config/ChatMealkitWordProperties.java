package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 메시지에서 밀키트를 원하는지 볼 때 쓰는 단어 (MealkitWordChecker). 비교는 공백을 뺀 글로 한다.
 * 부정어("말고·싫·안" 등)는 맛 단어 확인과 같은 단어(ChatTasteWordProperties)를 쓴다.
 *
 * @param mealkit 밀키트를 직접 말하는 단어 ("밀키트 추천해줘"). 있으면 밀키트만 보여 준다
 * @param easy    요리를 번거로워하는 말 ("간단하게", "귀찮아"). 있으면 레시피 카드 아래 밀키트 한 장을 붙인다
 */
@ConfigurationProperties(prefix = "app.chat.mealkit-words")
public record ChatMealkitWordProperties(
        @DefaultValue({"밀키트"})
        List<String> mealkit,
        @DefaultValue({"간단", "간편", "빨리", "귀찮", "요리하기싫"})
        List<String> easy
) {
}

package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 온도 말 (TemperatureWordChecker). 비교는 공백을 뺀 글로 한다.
 *
 * @param warm              따뜻한 요리를 원하는 말 ("따뜻한", "뜨끈한")
 * @param cold              차가운 요리를 원하는 말 ("차가운", "시원한")
 * @param coldDishes        이름에 들어 있으면 찬 요리인 말 ("냉국", "냉면"). "냉" 한 글자는 넣지 않는다 ("냉이")
 * @param hotDishEndings    이름이 이 말로 끝나면 뜨거운 국물 요리 ("탕", "찌개")
 * @param hotDishExclusions 끝말이 맞아도 뜨거운 국물 요리가 아닌 이름 ("그라탕")
 */
@ConfigurationProperties(prefix = "app.chat.temperature-words")
public record ChatTemperatureWordProperties(
        List<String> warm,
        List<String> cold,
        List<String> coldDishes,
        List<String> hotDishEndings,
        List<String> hotDishExclusions
) {

    public ChatTemperatureWordProperties {
        warm = warm == null ? List.of() : List.copyOf(warm);
        cold = cold == null ? List.of() : List.copyOf(cold);
        coldDishes = coldDishes == null ? List.of() : List.copyOf(coldDishes);
        hotDishEndings = hotDishEndings == null ? List.of() : List.copyOf(hotDishEndings);
        hotDishExclusions = hotDishExclusions == null ? List.of() : List.copyOf(hotDishExclusions);
    }
}

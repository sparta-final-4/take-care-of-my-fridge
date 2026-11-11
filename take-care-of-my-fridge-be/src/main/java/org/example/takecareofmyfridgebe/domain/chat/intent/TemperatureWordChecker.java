package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatTemperatureWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 추천 요청의 온도 말("따뜻한", "시원한")로 찬·뜨거운 요리를 가른다. 스프링에 의존하지 않는 순수 클래스.
 * <ul>
 *   <li>따뜻한 말만 있으면 이름에 찬 요리 말(냉국·냉면·빙수 등)이 든 레시피를 후보에서 뺀다</li>
 *   <li>차가운 말만 있으면 뜨거운 국물 요리(이름이 탕·전골·찌개로 끝남)를 뒤로 보내고, 빈자리를 채우는 데 쓰지 않는다</li>
 *   <li>둘 다 있거나 둘 다 없으면 보지 않는다</li>
 * </ul>
 * AI는 "따뜻한 국물"을 분류(국·찌개…)로만 바꿔서, 분류가 같은 냉국이 요청에 맞은 후보로 남는다. 그래서 서버가 이름으로 한 번 더 거른다.
 * 비교는 모두 공백을 뺀 글로 한다.
 */
public final class TemperatureWordChecker {

    public enum Temperature {
        WARM, COLD
    }

    private TemperatureWordChecker() {
    }

    /**
     * @param texts 메시지와 원한 표현 (null은 건너뛴다)
     * @return 원하는 온도. 따뜻한 말과 차가운 말이 둘 다 있거나 둘 다 없으면 빈 값
     */
    public static Optional<Temperature> wanted(ChatTemperatureWordProperties words, String... texts) {
        String text = Stream.of(texts)
                .filter(value -> value != null && !value.isBlank())
                .map(TemperatureWordChecker::compact)
                .collect(Collectors.joining(" "));
        boolean warm = containsAny(text, words.warm());
        boolean cold = containsAny(text, words.cold());
        if (warm == cold) {
            return Optional.empty();
        }
        return Optional.of(warm ? Temperature.WARM : Temperature.COLD);
    }

    // 이름에 찬 요리 말이 들어 있는지 ("오이냉국", "물냉면")
    public static boolean coldDish(String name, ChatTemperatureWordProperties words) {
        return name != null && containsAny(compact(name), words.coldDishes());
    }

    // 이름이 뜨거운 국물 끝말로 끝나는지 ("꽃게해물탕", "김치찌개"). 제외 이름("가지그라탕")은 아니다
    public static boolean hotDish(String name, ChatTemperatureWordProperties words) {
        if (name == null) {
            return false;
        }
        String compact = compact(name);
        return words.hotDishEndings().stream().anyMatch(compact::endsWith)
                && !containsAny(compact, words.hotDishExclusions());
    }

    /**
     * 원하는 온도로 후보를 정리한다. 따뜻하면 찬 요리를 빼고, 차가우면 뜨거운 국물 요리를 순서를 지킨 채 뒤로 보낸다.
     */
    public static List<RecipeCandidate> arrange(List<RecipeCandidate> candidates, Optional<Temperature> temperature,
                                                ChatTemperatureWordProperties words) {
        if (temperature.isEmpty()) {
            return candidates;
        }
        if (temperature.get() == Temperature.WARM) {
            return candidates.stream().filter(candidate -> !coldDish(candidate.name(), words)).toList();
        }
        return Stream.concat(
                candidates.stream().filter(candidate -> !hotDish(candidate.name(), words)),
                candidates.stream().filter(candidate -> hotDish(candidate.name(), words))
        ).toList();
    }

    /**
     * 원하는 온도와 반대인 후보 ID. 빈자리를 채우는 데 쓰지 않는다 (차가우면 뜨거운 국물 요리, 따뜻하면 찬 요리).
     */
    public static Set<Long> mismatched(List<RecipeCandidate> candidates, Optional<Temperature> temperature,
                                       ChatTemperatureWordProperties words) {
        if (temperature.isEmpty()) {
            return Set.of();
        }
        boolean warm = temperature.get() == Temperature.WARM;
        return candidates.stream()
                .filter(candidate -> warm ? coldDish(candidate.name(), words) : hotDish(candidate.name(), words))
                .map(RecipeCandidate::recipeId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(TemperatureWordChecker::compact).filter(word -> !word.isEmpty()).anyMatch(text::contains);
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }
}

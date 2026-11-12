package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.recommend.TemperatureWordChecker.Temperature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TemperatureWordCheckerTest {

    // config/chat-words.yml app.chat.words.temperature와 같은 목록
    public static final ChatWordProperties.Temperature WORDS = new ChatWordProperties.Temperature(
            List.of("따뜻", "따듯", "뜨끈", "뜨뜻", "따끈", "뜨거운", "뜨겁"),
            List.of("차가운", "차갑", "시원", "찬거", "찬걸", "찬음식"),
            List.of("냉국", "냉채", "냉면", "냉스프", "냉수프", "냉파스타", "냉우동", "냉소바", "냉메밀", "냉모밀", "냉라면", "냉짬뽕",
                    "콩국수", "빙수", "화채", "차가운", "아이스"),
            List.of("탕", "전골", "찌개", "국밥"),
            List.of("그라탕", "냉탕"));

    @Test
    @DisplayName("따뜻한 말만 있으면 따뜻함, 차가운 말만 있으면 차가움, 둘 다 있거나 없으면 빈 값이다")
    void readsWantedTemperature() {
        assertThat(TemperatureWordChecker.wanted(WORDS, "맵고 따뜻한 국물 레시피 추천해줘")).contains(Temperature.WARM);
        assertThat(TemperatureWordChecker.wanted(WORDS, "시원한 국물 요리")).contains(Temperature.COLD);
        assertThat(TemperatureWordChecker.wanted(WORDS, "따뜻한 거나 시원한 거")).isEmpty();
        assertThat(TemperatureWordChecker.wanted(WORDS, "국물 요리 추천해줘")).isEmpty();
    }

    @Test
    @DisplayName("다시 추천에서는 이번 말에 온도 말이 없어도 원한 표현의 온도 말을 본다")
    void readsTemperatureFromPreference_whenMessageHasNone() {
        assertThat(TemperatureWordChecker.wanted(WORDS, "다른 거 없어?", "따뜻한 국물")).contains(Temperature.WARM);
        assertThat(TemperatureWordChecker.wanted(WORDS, "다른 거 없어?", null)).isEmpty();
    }

    @Test
    @DisplayName("따뜻한 요리를 원하면 이름에 냉국·냉면이 든 레시피는 빼고 냉이된장찌개는 남긴다")
    void removesColdDishes_whenWarm() {
        List<RecipeCandidate> candidates = List.of(
                candidate(1L, "꽃게해물탕"), candidate(2L, "오이냉국"), candidate(3L, "냉이된장찌개"), candidate(4L, "물냉면"));

        List<RecipeCandidate> arranged = TemperatureWordChecker.arrange(candidates, Optional.of(Temperature.WARM), WORDS);

        assertThat(arranged).extracting(RecipeCandidate::recipeId).containsExactly(1L, 3L);
        assertThat(TemperatureWordChecker.mismatched(candidates, Optional.of(Temperature.WARM), WORDS))
                .containsExactlyInAnyOrder(2L, 4L);
    }

    @Test
    @DisplayName("차가운 요리를 원하면 이름이 탕·찌개로 끝나는 레시피를 순서를 지켜 뒤로 보내고 탕수육·그라탕은 그대로 둔다")
    void movesHotSoupsBack_whenCold() {
        List<RecipeCandidate> candidates = List.of(
                candidate(1L, "김치찌개"), candidate(2L, "오이냉국"), candidate(3L, "갈비탕"), candidate(4L, "탕수육"),
                candidate(5L, "가지그라탕"));

        List<RecipeCandidate> arranged = TemperatureWordChecker.arrange(candidates, Optional.of(Temperature.COLD), WORDS);

        assertThat(arranged).extracting(RecipeCandidate::recipeId).containsExactly(2L, 4L, 5L, 1L, 3L);
        assertThat(TemperatureWordChecker.mismatched(candidates, Optional.of(Temperature.COLD), WORDS))
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("온도 말이 없으면 후보를 그대로 둔다")
    void keepsCandidates_whenNoTemperature() {
        List<RecipeCandidate> candidates = List.of(candidate(1L, "오이냉국"), candidate(2L, "갈비탕"));

        assertThat(TemperatureWordChecker.arrange(candidates, Optional.empty(), WORDS)).isEqualTo(candidates);
        assertThat(TemperatureWordChecker.mismatched(candidates, Optional.empty(), WORDS)).isEmpty();
    }

    private static RecipeCandidate candidate(Long id, String name) {
        return new RecipeCandidate(id, name, "국", 20, new Ranking(0, 0, 0, 0, 1, 1, 0),
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(), List.of(), List.of(), null));
    }
}

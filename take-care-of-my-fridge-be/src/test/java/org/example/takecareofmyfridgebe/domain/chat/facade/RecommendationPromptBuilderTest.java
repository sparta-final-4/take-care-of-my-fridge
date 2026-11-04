package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPromptBuilderTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long TOFU = 21L;
    private static final Long MUSHROOM = 45L;
    private static final Long CUCUMBER = 15L;
    private static final Map<Long, String> NAMES = Map.of(TOFU, "두부", MUSHROOM, "버섯", CUCUMBER, "오이");

    @Test
    @DisplayName("사용자 요청과 후보를 줄 글로 만들고 맛은 한글, 임박 재료는 오늘까지·3일 남음처럼 말로 쓴다")
    void buildsCandidateLines() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(MUSHROOM, TOFU), List.of(Taste.SPICY), List.of("찌개", "국"),
                30, List.of(CUCUMBER), true);
        RecipeCandidate first = candidate(1L, "두부버섯찌개", "찌개", 25,
                reason(List.of("양파", "대파", "두부"), List.of(new ImminentIngredient("양파", 0), new ImminentIngredient("대파", 3)),
                        List.of(), List.of(Taste.SPICY),
                        List.of(missing(1L, "돼지고기"), missing(2L, "부추"), missing(3L, "당근"), missing(4L, "애호박"), missing(5L, "고추")),
                        true));
        RecipeCandidate second = candidate(2L, "버섯전", "전", null,
                reason(List.of(), List.of(), List.of(Taste.SALTY, Taste.UMAMI), List.of(), List.of(), false));

        String text = RecommendationPromptBuilder.build("버섯 두부로 매운 찌개", query, NAMES, List.of(first, second));

        assertThat(text).isEqualTo("""
                [사용자 요청]
                메시지: 버섯 두부로 매운 찌개
                선택 재료: 버섯, 두부 (이 재료를 꼭 쓰는 레시피만)
                조건: 원하는 맛 매운맛 / 분류 찌개, 국 / 30분 이내 / 뺄 재료 오이
                [추천 후보]
                1. 두부버섯찌개 | 찌개 | 조리 시간 25분 | 쓰는 냉장고 재료: 양파(오늘까지), 대파(3일 남음), 두부 | 원하는 맛과 맞음: 매운맛 | 부족한 재료: 돼지고기, 부추, 당근 외 2개 | 재료 많이 모자람
                2. 버섯전 | 전 | 조리 시간 정보 없음 | 쓰는 냉장고 재료: 없음 | 좋아하는 맛과 맞음: 짠맛, 감칠맛 | 부족한 재료: 없음""");
    }

    @Test
    @DisplayName("후보가 없으면 [추천 후보] 아래에 후보 없음을 쓰고, 조건이 없으면 없음으로 쓴다")
    void writesNoCandidates_whenEmpty() {
        String text = RecommendationPromptBuilder.build("뭐 먹지?", RecipeQuery.of(MEMBER_ID, List.of()), NAMES, List.of());

        assertThat(text).isEqualTo("""
                [사용자 요청]
                메시지: 뭐 먹지?
                선택 재료: 없음
                조건: 없음
                [추천 후보]
                후보 없음""");
    }

    @Test
    @DisplayName("메시지·레시피 이름의 중괄호는 괄호로 바꿔 넣는다")
    void replacesBraces() {
        RecipeCandidate candidate = candidate(1L, "{특제} 찌개", "찌개", 10,
                reason(List.of(), List.of(), List.of(), List.of(), List.of(), false));

        String text = RecommendationPromptBuilder.build("{name} 추천", RecipeQuery.of(MEMBER_ID, List.of()), NAMES,
                List.of(candidate));

        assertThat(text).doesNotContain("{", "}").contains("메시지: (name) 추천", "1. (특제) 찌개");
    }

    @Test
    @DisplayName("냉장고에 없는 선택 재료가 있으면 후보 줄에 따로 쓴다")
    void writesMissingSelected() {
        RecipeCandidate candidate = candidate(1L, "표고두부전골", "전골", 30,
                new Reason(List.of("대파"), List.of(), List.of(), List.of(), List.of(), false, false,
                        List.of(missing(161L, "표고버섯"), missing(TOFU, "두부")), List.of("표고버섯", "두부")));

        String text = RecommendationPromptBuilder.build("버섯 두부로", RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM, TOFU)),
                NAMES, List.of(candidate));

        assertThat(text).contains("1. 표고두부전골 | 전골 | 조리 시간 30분 | 쓰는 냉장고 재료: 대파 | 냉장고에 없는 선택 재료: 표고버섯, 두부 | 부족한 재료: 표고버섯, 두부");
    }

    @Test
    @DisplayName("소비기한은 D-0 오늘까지, D-1 내일까지, D-2 모레까지, D-3 3일 남음으로 쓴다")
    void writesDdayInWords() {
        assertThat(RecommendationPromptBuilder.dDayLabel(0)).isEqualTo("오늘까지");
        assertThat(RecommendationPromptBuilder.dDayLabel(1)).isEqualTo("내일까지");
        assertThat(RecommendationPromptBuilder.dDayLabel(2)).isEqualTo("모레까지");
        assertThat(RecommendationPromptBuilder.dDayLabel(3)).isEqualTo("3일 남음");
    }

    private static RecipeCandidate candidate(Long id, String name, String category, Integer cookTimeMin, Reason reason) {
        return new RecipeCandidate(id, name, category, cookTimeMin, new Ranking(0, 0, 0, 0, 1, 1, 0), reason);
    }

    private static Reason reason(List<String> used, List<ImminentIngredient> imminent, List<Taste> tastes,
                                 List<Taste> wantedTastes, List<MissingIngredient> missing, boolean lacks) {
        return new Reason(used, imminent, tastes, wantedTastes, List.of(), false, lacks, missing, List.of());
    }

    private static MissingIngredient missing(Long id, String name) {
        return new MissingIngredient(id, name);
    }
}

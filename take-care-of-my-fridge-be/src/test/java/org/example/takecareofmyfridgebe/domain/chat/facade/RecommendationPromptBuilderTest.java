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
    @DisplayName("사용자 요청과 후보를 줄 글로 만들고 맛은 한글, 임박 재료는 오늘까지인 양파처럼 말 순서로 쓴다")
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

        String text = RecommendationPromptBuilder.build("버섯 두부로 매운 찌개", query, NAMES, List.of(first, second), false);

        assertThat(text).isEqualTo("""
                [사용자 요청]
                메시지: 버섯 두부로 매운 찌개
                선택 재료: 버섯, 두부 (이 재료를 꼭 쓰는 레시피만)
                조건: 원하는 맛 매운맛 / 분류 찌개, 국 / 30분 이내 / 뺄 재료 오이
                [추천 후보]
                후보: 두부버섯찌개 | 찌개 | 조리 시간 25분 | 카드 이유: 매콤한 요리예요 | 쓰는 냉장고 재료: 오늘까지인 양파, 3일 남은 대파, 두부 | 원하는 맛과 맞음: 매운맛 | 장볼 재료: 돼지고기, 부추, 당근 외 2개 | 재료 많이 모자람
                후보: 버섯전 | 전 | 조리 시간 정보 없음 | 카드 이유: 짭짤하고 감칠맛 나요 | 쓰는 냉장고 재료: 없음 | 좋아하는 맛과 맞음: 짠맛, 감칠맛 | 장볼 재료: 없음""");
    }

    @Test
    @DisplayName("후보가 없으면 [추천 후보] 아래에 후보 없음을 쓰고, 조건이 없으면 없음으로 쓴다")
    void writesNoCandidates_whenEmpty() {
        String text = RecommendationPromptBuilder.build("뭐 먹지?", RecipeQuery.of(MEMBER_ID, List.of()), NAMES, List.of(), false);

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
                List.of(candidate), false);

        assertThat(text).doesNotContain("{", "}").contains("메시지: (name) 추천", "후보: (특제) 찌개");
    }

    @Test
    @DisplayName("고른 후보에 냉장고에 없는 선택 재료가 있으면 맨 위 [먼저 말할 것]에 모아 넣고, 후보 줄에 선택 재료 쓰임을 쓴다")
    void writesNoticeFirst_whenSelectedIsMissing() {
        RecipeCandidate first = candidate(1L, "표고두부전골", "전골", 30,
                new Reason(List.of("대파"), List.of(new ImminentIngredient("대파", 1)), List.of(), List.of(), List.of(), false, false,
                        List.of(missing(161L, "표고버섯"), missing(TOFU, "두부")), List.of("표고버섯", "두부"),
                        List.of("표고버섯", "두부"), null));
        RecipeCandidate second = candidate(2L, "팽이두부국", "국", 20,
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false,
                        List.of(missing(185L, "팽이버섯"), missing(TOFU, "두부")), List.of("팽이버섯", "두부"),
                        List.of("팽이버섯", "두부"), null));

        String text = RecommendationPromptBuilder.build("버섯 두부로", RecipeQuery.of(MEMBER_ID, List.of(MUSHROOM, TOFU)),
                NAMES, List.of(first, second), false);

        assertThat(text).startsWith("""
                [먼저 말할 것]
                말씀하신 표고버섯, 두부, 팽이버섯은 지금 냉장고에 없어서 준비가 필요해요
                [사용자 요청]""");
        assertThat(text).contains("후보: 표고두부전골 | 전골 | 조리 시간 30분 | 카드 이유: 표고버섯·두부가 주재료예요 | 선택 재료 쓰임: 표고버섯, 두부 | 쓰는 냉장고 재료: 내일까지인 대파 | 냉장고에 없는 선택 재료: 표고버섯, 두부 | 장볼 재료: 표고버섯, 두부");
    }

    @Test
    @DisplayName("키워드로 이름·재료가 맞은 레시피가 없으면 [먼저 말할 것]에 사용자가 말한 요리 이름으로 안내를 넣는다")
    void writesKeywordNotFoundNotice() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("면류"), null, null, false,
                List.of("냉파스타"), "냉파스타");

        String text = RecommendationPromptBuilder.build("냉파스타 먹고 싶어", query, NAMES, List.of(), true);

        assertThat(text).startsWith("""
                [먼저 말할 것]
                말씀하신 냉파스타 요리는 찾지 못해서 비슷한 걸 골랐어요
                [사용자 요청]""");
    }

    @Test
    @DisplayName("원한 표현이 있는데 고른 레시피가 요청(키워드·분류·맛)에 하나도 안 맞으면 [먼저 말할 것]에 솔직하게 넣는다")
    void writesPreferenceUnmetNotice() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("샐러드", "냉채"), null, "차가운 요리");
        RecipeCandidate unmatched = candidate(1L, "장어조림", "조림", null,
                reason(List.of("고추"), List.of(), List.of(), List.of(), List.of(), false));

        String text = RecommendationPromptBuilder.build("차가운 요리 추천해줘", query, NAMES, List.of(unmatched), true);

        assertThat(text).startsWith("""
                [먼저 말할 것]
                말씀하신 차가운 요리는 찾지 못해서 냉장고 재료로 만들 수 있는 요리를 골랐어요
                [사용자 요청]""");
        assertThat(text).doesNotContain("비슷한 걸 골랐어요");
    }

    @Test
    @DisplayName("고른 레시피 중 하나라도 요청에 맞으면 원한 표현 안내를 넣지 않는다")
    void omitsPreferenceNotice_whenAnyPickedMatchesRequest() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("냉면"), "냉면", "냉면 같은 면 요리");
        RecipeCandidate matched = new RecipeCandidate(1L, "열무김치냉면", "면류", 20, new Ranking(1, 0, 0, 0, 1, 1, 0),
                reason(List.of(), List.of(), List.of(), List.of(), List.of(), false));

        assertThat(RecommendationPromptBuilder.build("냉면", query, NAMES, List.of(matched), false))
                .doesNotContain("[먼저 말할 것]");
    }

    @Test
    @DisplayName("원한 표현이 없으면(뭐 먹지?) 요청에 안 맞아도 [먼저 말할 것]을 넣지 않는다")
    void omitsNotice_whenNoPreference() {
        RecipeCandidate candidate = candidate(1L, "장어조림", "조림", null,
                reason(List.of("고추"), List.of(), List.of(), List.of(), List.of(), false));

        assertThat(RecommendationPromptBuilder.build("뭐 먹지?", RecipeQuery.of(MEMBER_ID, List.of()), NAMES,
                List.of(candidate), false)).doesNotContain("[먼저 말할 것]");
    }

    @Test
    @DisplayName("고른 후보에 냉장고에 없는 선택 재료가 없으면 [먼저 말할 것]을 넣지 않는다")
    void omitsNotice_whenNoSelectedIsMissing() {
        RecipeCandidate candidate = candidate(1L, "두부조림", "조림", 20,
                new Reason(List.of("두부"), List.of(), List.of(), List.of(), List.of(), false, false,
                        List.of(), List.of(), List.of("두부"), null));

        String text = RecommendationPromptBuilder.build("두부로", RecipeQuery.of(MEMBER_ID, List.of(TOFU)), NAMES,
                List.of(candidate), false);

        assertThat(text).startsWith("[사용자 요청]").doesNotContain("[먼저 말할 것]", "냉장고에 없는 선택 재료");
        assertThat(text).contains("선택 재료 쓰임: 두부");
    }

    @Test
    @DisplayName("안내 문장은 마지막 재료 받침에 따라 은/는을 붙인다")
    void choosesTopicParticleByLastName() {
        assertThat(RecommendationPromptBuilder.missingSelectedNotice(List.of(candidate(1L, "a", "국", 10,
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                        List.of("두부"), List.of(), null)))))
                .isEqualTo("말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요");
        assertThat(RecommendationPromptBuilder.missingSelectedNotice(List.of(candidate(1L, "a", "국", 10,
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                        List.of("표고버섯"), List.of(), null)))))
                .isEqualTo("말씀하신 표고버섯은 지금 냉장고에 없어서 준비가 필요해요");
    }

    @Test
    @DisplayName("소비기한은 재료 앞에 오늘까지인·내일까지인·모레까지인·3일 남은으로 붙인다")
    void writesDdayInWords() {
        assertThat(RecommendationPromptBuilder.dDayLabel(0)).isEqualTo("오늘까지인");
        assertThat(RecommendationPromptBuilder.dDayLabel(1)).isEqualTo("내일까지인");
        assertThat(RecommendationPromptBuilder.dDayLabel(2)).isEqualTo("모레까지인");
        assertThat(RecommendationPromptBuilder.dDayLabel(3)).isEqualTo("3일 남은");
    }

    private static RecipeCandidate candidate(Long id, String name, String category, Integer cookTimeMin, Reason reason) {
        return new RecipeCandidate(id, name, category, cookTimeMin, new Ranking(0, 0, 0, 0, 1, 1, 0), reason);
    }

    private static Reason reason(List<String> used, List<ImminentIngredient> imminent, List<Taste> tastes,
                                 List<Taste> wantedTastes, List<MissingIngredient> missing, boolean lacks) {
        return new Reason(used, imminent, tastes, wantedTastes, List.of(), false, lacks, missing, List.of(), List.of(), null);
    }

    private static MissingIngredient missing(Long id, String name) {
        return new MissingIngredient(id, name);
    }
}

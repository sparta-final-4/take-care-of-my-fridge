package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevDecisionLog.JevSide;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevDecisionLog.FinalSide;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// "상담 의도 Jev 결정" 로그 한 줄의 칸 이름·순서·값
class JevDecisionLogTest {

    static final List<String> FIELDS = List.of("memberId", "sessionId", "messageLength", "finalIntent", "finalFollows", "jevIntent",
            "jevIntentConfidence", "jevFollowsPrevious", "finalWanted", "finalAvoided", "jevWanted", "jevAvoided",
            "finalCategories", "jevRequestKind", "jevRequestKindConfidence", "jevCategory", "jevCategoryConfidence",
            "jevCategory2", "jevCategory2Confidence", "finalMealkit", "jevMealkit", "jevAsksQuestion",
            "jevDishNamed", "jevModel", "jevRequestIds", "jevLatencyMs", "jevInputTokens", "jevStatus", "decision", "jevUsedKind",
            "jevUsedValues", "jevUsedReason", "selfHarmBy");

    private static final FinalSide RESULT = new FinalSide(1L, 10L, 10, ChatIntent.RECOMMEND, false,
            List.of(Taste.SPICY), List.of(), List.of("찌개"), false);
    private static final JevDecision BY_JEV = new JevDecision(JevDecision.Decision.JEV, RequestKind.CATEGORY, List.of("찌개"));

    @Test
    @DisplayName("1차·2차가 모두 있으면 정해진 칸 이름 순서로 최종 결과와 Jev 판단(요청 종류·1·2등 분류, 0.5 이상인 밀키트)과 결정을 남긴다")
    void writesAllFieldsInOrder_whenBothCallsSucceed() {
        String line = JevDecisionLog.line(RESULT, JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 520L), BY_JEV);

        assertThat(line).startsWith("상담 의도 Jev 결정 - ");
        assertThat(keys(line)).containsExactlyElementsOf(FIELDS);
        assertThat(values(line))
                .containsEntry("memberId", "1").containsEntry("sessionId", "10").containsEntry("messageLength", "10")
                .containsEntry("finalIntent", "RECOMMEND").containsEntry("finalFollows", "false").containsEntry("jevIntent", "RECOMMEND")
                .containsEntry("jevIntentConfidence", "0.97").containsEntry("jevFollowsPrevious", "-")
                .containsEntry("finalWanted", "SPICY").containsEntry("finalAvoided", "")
                .containsEntry("jevWanted", "SPICY").containsEntry("jevAvoided", "")
                .containsEntry("finalCategories", "찌개").containsEntry("jevRequestKind", "CATEGORY")
                .containsEntry("jevRequestKindConfidence", "0.88").containsEntry("jevCategory", "찌개").containsEntry("jevCategoryConfidence", "0.81")
                .containsEntry("jevCategory2", "국").containsEntry("jevCategory2Confidence", "0.12")
                .containsEntry("finalMealkit", "false").containsEntry("jevMealkit", "false")
                .containsEntry("jevAsksQuestion", "0.05").containsEntry("jevDishNamed", "0.09")
                .containsEntry("jevModel", "jev-1.14.0").containsEntry("jevRequestIds", "req_first/req_second")
                .containsEntry("jevLatencyMs", "210/290/520").containsEntry("jevInputTokens", "417/1893")
                .containsEntry("jevStatus", "OK")
                .containsEntry("decision", "JEV").containsEntry("jevUsedKind", "CATEGORY").containsEntry("jevUsedValues", "찌개")
                .containsEntry("jevUsedReason", "NONE");
    }

    @Test
    @DisplayName("Jev로 정하면 decision=JEV와 쓴 요청 종류·값을 남기고 값 안의 공백은 빼며, 서버 답이면 쓴 요청 종류·값은 -다")
    void writesDecisionAndUsedValues_whenJevDecides() {
        JevDecision broad = new JevDecision(JevDecision.Decision.JEV, RequestKind.BROAD, List.of("오징어볶음", "토마토 스파게티"));

        Map<String, String> values = values(JevDecisionLog.line(RESULT,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 520L), broad));
        Map<String, String> openAiFailed = values(JevDecisionLog.line(RESULT,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 520L),
                JevDecision.of(JevDecision.Decision.OPENAI_FAILED_SERVER_FALLBACK)));

        assertThat(values).containsEntry("decision", "JEV").containsEntry("jevUsedKind", "BROAD")
                .containsEntry("jevUsedValues", "오징어볶음,토마토스파게티");
        assertThat(openAiFailed).containsEntry("decision", "OPENAI_FAILED_SERVER_FALLBACK").containsEntry("jevUsedKind", "-")
                .containsEntry("jevUsedValues", "-").containsEntry("jevUsedReason", "-");
    }

    @Test
    @DisplayName("Jev가 confidence가 낮은 넓은 요청으로 정해도 그대로 JEV와 넓은 요청 요리 이름을, 직전 추천에 이어지는 말이면 이어짐 확률을 남긴다")
    void writesBroadAndFollowsPrevious_whenJevDecidesWithLowConfidence() {
        JevIntentJudgement base = judgement(ChatIntent.RECOMMEND, true);
        JevIntentJudgement.First first = base.first();
        JevIntentJudgement following = new JevIntentJudgement(new JevIntentJudgement.First(ChatIntent.RECOMMEND, ChatIntent.RECOMMEND,
                0.72, first.intentProbabilities(), 0.83, first.asksQuestion(), first.fridgeScope(), first.fridgeScopeConfidence(),
                first.call()), base.second(), base.totalLatencyMs());
        JevDecision broad = new JevDecision(JevDecision.Decision.JEV, RequestKind.BROAD, List.of("감자국", "떡국"));

        Map<String, String> values = values(JevDecisionLog.line(RESULT, JevSide.finished(Optional.of(following), 520L), broad));

        assertThat(values).containsEntry("jevIntent", "RECOMMEND").containsEntry("jevFollowsPrevious", "0.83")
                .containsEntry("jevIntentConfidence", "0.72").containsEntry("decision", "JEV").containsEntry("jevUsedKind", "BROAD")
                .containsEntry("jevUsedValues", "감자국,떡국");
    }

    @Test
    @DisplayName("Jev로 추천 + 이어짐을 정하면 finalFollows=true와 Jev 이어짐 확률을 남기고, 이어짐이 아니면 finalFollows=false다")
    void writesFinalFollows_whenRecommendFollowsPrevious() {
        JevIntentJudgement base = judgement(ChatIntent.RECOMMEND, true);
        JevIntentJudgement.First first = base.first();
        JevIntentJudgement following = new JevIntentJudgement(new JevIntentJudgement.First(ChatIntent.RECOMMEND, ChatIntent.RECOMMEND,
                0.97, first.intentProbabilities(), 0.8, first.asksQuestion(), first.fridgeScope(), first.fridgeScopeConfidence(),
                first.call()), base.second(), base.totalLatencyMs());
        FinalSide follows = new FinalSide(1L, 10L, 10, ChatIntent.RECOMMEND, true, List.of(Taste.SPICY), List.of(), List.of("찌개"), false);

        Map<String, String> values = values(JevDecisionLog.line(follows, JevSide.finished(Optional.of(following), 520L), BY_JEV));
        Map<String, String> fresh = values(JevDecisionLog.line(RESULT, JevSide.finished(Optional.of(base), 520L), BY_JEV));

        assertThat(values).containsEntry("finalIntent", "RECOMMEND").containsEntry("finalFollows", "true")
                .containsEntry("jevIntent", "RECOMMEND").containsEntry("jevFollowsPrevious", "0.8").containsEntry("decision", "JEV");
        assertThat(fresh).containsEntry("finalFollows", "false");
    }

    @Test
    @DisplayName("특정 요리인데 요리 이름이 없어 1등 분류로 가면 Jev 원래 요청 종류와 바뀐 뒤 쓴 요청 종류를 따로 남긴다")
    void writesOriginalAndUsedKind_whenDishFallsBackToTopCategory() {
        JevIntentJudgement base = judgement(ChatIntent.RECOMMEND, true);
        JevIntentJudgement.Second second = base.second();
        JevIntentJudgement dish = new JevIntentJudgement(base.first(), new JevIntentJudgement.Second(0.81, second.featureNamed(),
                second.ingredientNamed(), second.mealkitWanted(), second.tastes(), RequestKind.DISH, 0.77, "면류", 0.83, "국", 0.1, second.call()),
                base.totalLatencyMs());
        JevDecision category = new JevDecision(JevDecision.Decision.JEV, RequestKind.CATEGORY, List.of("면류"),
                JevDecision.Reason.DISH_TO_TOP_CATEGORY);

        Map<String, String> values = values(JevDecisionLog.line(RESULT, JevSide.finished(Optional.of(dish), 520L), category));

        assertThat(values).containsEntry("jevRequestKind", "DISH").containsEntry("jevCategory", "면류")
                .containsEntry("decision", "JEV").containsEntry("jevUsedKind", "CATEGORY").containsEntry("jevUsedValues", "면류")
                .containsEntry("jevUsedReason", "DISH_TO_TOP_CATEGORY");
    }

    @Test
    @DisplayName("2차를 묻지 않은 의도면 2차 칸은 -로 남긴다")
    void writesNoneForSecondFields_whenSecondNotAsked() {
        FinalSide general = new FinalSide(1L, 10L, 12, ChatIntent.FRIDGE, false, List.of(), List.of(), List.of(), false);

        Map<String, String> values = values(JevDecisionLog.line(general,
                JevSide.finished(Optional.of(judgement(ChatIntent.FRIDGE, false)), 230L), new JevDecision(JevDecision.Decision.JEV, null, List.of())));

        assertThat(values).containsEntry("finalIntent", "FRIDGE").containsEntry("jevIntent", "FRIDGE")
                .containsEntry("jevWanted", "-").containsEntry("jevRequestKind", "-").containsEntry("jevCategory", "-")
                .containsEntry("jevCategory2", "-").containsEntry("jevMealkit", "-").containsEntry("jevDishNamed", "-")
                .containsEntry("jevRequestIds", "req_first/-").containsEntry("jevLatencyMs", "210/-/230")
                .containsEntry("jevInputTokens", "417/-").containsEntry("jevStatus", "OK");
    }

    @Test
    @DisplayName("추천인데 2차가 없으면 SECOND_FAILED, 1차가 없으면 FIRST_FAILED, 건너뛰면 SKIPPED로 같은 칸을 남긴다")
    void writesStatus_whenJevFailsOrSkips() {
        JevDecision failed = JevDecision.of(JevDecision.Decision.JEV_FAILED_SERVER_FALLBACK);
        Map<String, String> second = values(JevDecisionLog.line(RESULT,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, false)), 3100L), failed));
        Map<String, String> first = values(JevDecisionLog.line(RESULT, JevSide.finished(Optional.empty(), 3005L), failed));
        Map<String, String> skipped = values(JevDecisionLog.line(RESULT, JevSide.skipped(), failed));

        assertThat(second).containsEntry("jevStatus", "SECOND_FAILED").containsEntry("jevWanted", "-");
        assertThat(first).containsEntry("jevStatus", "FIRST_FAILED").containsEntry("jevIntent", "-")
                .containsEntry("jevLatencyMs", "-/-/3005").containsEntry("decision", "JEV_FAILED_SERVER_FALLBACK")
                .containsEntry("finalIntent", "RECOMMEND").containsEntry("finalWanted", "SPICY");
        assertThat(skipped).containsEntry("jevStatus", "SKIPPED").containsEntry("jevLatencyMs", "-");
        assertThat(keys(JevDecisionLog.line(RESULT, JevSide.skipped(), failed))).containsExactlyElementsOf(FIELDS);
    }

    @Test
    @DisplayName("최종 피할 맛·밀키트와 Jev 맛 답을 따로 남긴다")
    void writesFinalAndJevTastesSeparately_whenTheyDiffer() {
        FinalSide avoidSpicy = new FinalSide(1L, 10L, 9, ChatIntent.RECOMMEND, false,
                List.of(), List.of(Taste.SPICY), List.of("국"), true);

        Map<String, String> values = values(JevDecisionLog.line(avoidSpicy,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 500L), BY_JEV));

        assertThat(values).containsEntry("finalAvoided", "SPICY").containsEntry("jevAvoided", "")
                .containsEntry("jevWanted", "SPICY").containsEntry("finalCategories", "국").containsEntry("finalMealkit", "true");
    }

    @Test
    @DisplayName("1등 분류가 없음이면 jevCategory는 -이고 확률과 2등 분류는 남긴다")
    void writesNoneCategory_whenTopCategoryIsNone() {
        JevIntentJudgement broad = judgement(ChatIntent.RECOMMEND, true);
        JevIntentJudgement.Second second = broad.second();
        JevIntentJudgement withBroad = new JevIntentJudgement(broad.first(), new JevIntentJudgement.Second(
                second.dishNamed(), second.featureNamed(), second.ingredientNamed(), second.mealkitWanted(), second.tastes(),
                RequestKind.BROAD, 0.77, null, 0.6, "국", 0.3, second.call()), broad.totalLatencyMs());

        Map<String, String> values = values(JevDecisionLog.line(RESULT, JevSide.finished(Optional.of(withBroad), 520L), BY_JEV));

        assertThat(values).containsEntry("jevRequestKind", "BROAD").containsEntry("jevRequestKindConfidence", "0.77")
                .containsEntry("jevCategory", "-").containsEntry("jevCategoryConfidence", "0.6")
                .containsEntry("jevCategory2", "국").containsEntry("jevCategory2Confidence", "0.3");
    }

    // 1차: 의도 0.97, 묻는 말 0.05. 2차: 매운맛 원함, 특정 분류 0.88, 1등 분류 찌개 0.81, 밀키트 0.08
    static JevIntentJudgement judgement(ChatIntent intent, boolean withSecond) {
        Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
        Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, each == intent ? 0.98 : 0.0));
        JevIntentJudgement.First first = new JevIntentJudgement.First(intent, intent, 0.97, probabilities, null, 0.05, FridgeScope.ALL, 0.9,
                new JevIntentJudgement.Call("jev-1.14.0", "req_first", 210L, 417));
        if (!withSecond) {
            return new JevIntentJudgement(first, null, 230L);
        }
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste,
                new TasteAnswer(taste == Taste.SPICY ? TasteStance.WANTED : TasteStance.NOT_MENTIONED, 0.9)));
        JevIntentJudgement.Second second = new JevIntentJudgement.Second(0.09, 0.74, 0.07, 0.08, tastes,
                RequestKind.CATEGORY, 0.88, "찌개", 0.81, "국", 0.12,
                new JevIntentJudgement.Call("jev-1.14.0", "req_second", 290L, 1893));
        return new JevIntentJudgement(first, second, 520L);
    }

    static List<String> keys(String line) {
        return values(line).keySet().stream().toList();
    }

    static Map<String, String> values(String line) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : line.substring(line.indexOf(" - ") + 3).split(" ")) {
            int at = part.indexOf('=');
            values.put(part.substring(0, at), part.substring(at + 1));
        }
        return values;
    }
}

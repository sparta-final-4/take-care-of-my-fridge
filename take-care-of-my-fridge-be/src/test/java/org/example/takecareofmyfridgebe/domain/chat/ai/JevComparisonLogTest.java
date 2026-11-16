package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevComparisonLog.JevSide;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevComparisonLog.OpenAiSide;
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

// "상담 의도 Jev 비교" 로그 한 줄의 칸 이름·순서·값
class JevComparisonLogTest {

    static final List<String> FIELDS = List.of("memberId", "sessionId", "messageLength", "openaiIntent", "jevIntent",
            "jevIntentConfidence", "intentMatch", "openaiWanted", "openaiAvoided", "jevWanted", "jevAvoided", "tasteMatch",
            "openaiCategories", "jevRequestKind", "jevRequestKindConfidence", "jevCategory", "jevCategoryConfidence",
            "categoryMatch", "openaiMealkit", "jevMealkit", "jevAsksQuestion",
            "jevDishNamed", "jevModel", "jevRequestIds", "jevLatencyMs", "jevInputTokens", "jevStatus", "decision", "jevUsedKind",
            "jevUsedValues");

    private static final OpenAiSide OPENAI = new OpenAiSide(1L, 10L, 10, ChatIntent.RECOMMEND,
            List.of(Taste.SPICY), List.of(), List.of("찌개"), false);

    @Test
    @DisplayName("1차·2차가 모두 있으면 정해진 칸 이름 순서로 값을 남기고 요청 종류·1등 분류와 0.5 이상인 밀키트를 Jev 쪽으로 본다")
    void writesAllFieldsInOrder_whenBothCallsSucceed() {
        String line = JevComparisonLog.line(OPENAI, JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 520L));

        assertThat(line).startsWith("상담 의도 Jev 비교 - ");
        assertThat(keys(line)).containsExactlyElementsOf(FIELDS);
        assertThat(values(line))
                .containsEntry("memberId", "1").containsEntry("sessionId", "10").containsEntry("messageLength", "10")
                .containsEntry("openaiIntent", "RECOMMEND").containsEntry("jevIntent", "RECOMMEND")
                .containsEntry("jevIntentConfidence", "0.97").containsEntry("intentMatch", "true")
                .containsEntry("openaiWanted", "SPICY").containsEntry("openaiAvoided", "")
                .containsEntry("jevWanted", "SPICY").containsEntry("jevAvoided", "").containsEntry("tasteMatch", "true")
                .containsEntry("openaiCategories", "찌개").containsEntry("jevRequestKind", "CATEGORY")
                .containsEntry("jevRequestKindConfidence", "0.88").containsEntry("jevCategory", "찌개").containsEntry("jevCategoryConfidence", "0.81")
                .containsEntry("categoryMatch", "true")
                .containsEntry("openaiMealkit", "false").containsEntry("jevMealkit", "false")
                .containsEntry("jevAsksQuestion", "0.05").containsEntry("jevDishNamed", "0.09")
                .containsEntry("jevModel", "jev-1.14.0").containsEntry("jevRequestIds", "req_first/req_second")
                .containsEntry("jevLatencyMs", "210/290/520").containsEntry("jevInputTokens", "417/1893")
                .containsEntry("jevStatus", "OK")
                .containsEntry("decision", "SHADOW").containsEntry("jevUsedKind", "-").containsEntry("jevUsedValues", "-");
    }

    @Test
    @DisplayName("on 모드에서 Jev로 정하면 decision=JEV와 쓴 요청 종류·값을 남기고, 값 안의 공백은 뺀다")
    void writesDecisionAndUsedValues_whenJevDecides() {
        JevDecision broad = new JevDecision(JevDecision.Decision.JEV, RequestKind.BROAD, List.of("오징어볶음", "토마토 스파게티"));

        Map<String, String> values = values(JevComparisonLog.line(OPENAI,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 520L), broad));
        Map<String, String> lowConfidence = values(JevComparisonLog.line(OPENAI,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 520L),
                JevDecision.of(JevDecision.Decision.OPENAI_LOW_CONFIDENCE)));

        assertThat(values).containsEntry("decision", "JEV").containsEntry("jevUsedKind", "BROAD")
                .containsEntry("jevUsedValues", "오징어볶음,토마토스파게티");
        assertThat(lowConfidence).containsEntry("decision", "OPENAI_LOW_CONFIDENCE").containsEntry("jevUsedKind", "-")
                .containsEntry("jevUsedValues", "-");
    }

    @Test
    @DisplayName("의도가 다르면 intentMatch=false이고, 2차를 묻지 않은 의도면 2차 칸은 -로 남긴다")
    void writesNoneForSecondFields_whenSecondNotAsked() {
        OpenAiSide general = new OpenAiSide(1L, 10L, 12, ChatIntent.GENERAL, List.of(), List.of(), List.of(), false);

        Map<String, String> values = values(JevComparisonLog.line(general,
                JevSide.finished(Optional.of(judgement(ChatIntent.FRIDGE, false)), 230L)));

        assertThat(values).containsEntry("intentMatch", "false").containsEntry("jevIntent", "FRIDGE")
                .containsEntry("jevWanted", "-").containsEntry("tasteMatch", "-").containsEntry("jevRequestKind", "-").containsEntry("jevCategory", "-")
                .containsEntry("categoryMatch", "-").containsEntry("jevMealkit", "-").containsEntry("jevDishNamed", "-")
                .containsEntry("jevRequestIds", "req_first/-").containsEntry("jevLatencyMs", "210/-/230")
                .containsEntry("jevInputTokens", "417/-").containsEntry("jevStatus", "OK");
    }

    @Test
    @DisplayName("추천인데 2차가 없으면 SECOND_FAILED, 1차가 없으면 FIRST_FAILED, 건너뛰면 SKIPPED로 같은 칸을 남긴다")
    void writesStatus_whenJevFailsOrSkips() {
        Map<String, String> second = values(JevComparisonLog.line(OPENAI,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, false)), 3100L)));
        Map<String, String> first = values(JevComparisonLog.line(OPENAI, JevSide.finished(Optional.empty(), 3005L)));
        Map<String, String> skipped = values(JevComparisonLog.line(OPENAI, JevSide.skipped()));

        assertThat(second).containsEntry("jevStatus", "SECOND_FAILED").containsEntry("intentMatch", "true")
                .containsEntry("tasteMatch", "-");
        assertThat(first).containsEntry("jevStatus", "FIRST_FAILED").containsEntry("jevIntent", "-")
                .containsEntry("intentMatch", "-").containsEntry("jevLatencyMs", "-/-/3005")
                .containsEntry("openaiIntent", "RECOMMEND").containsEntry("openaiWanted", "SPICY");
        assertThat(skipped).containsEntry("jevStatus", "SKIPPED").containsEntry("jevLatencyMs", "-");
        assertThat(keys(JevComparisonLog.line(OPENAI, JevSide.skipped()))).containsExactlyElementsOf(FIELDS);
    }

    @Test
    @DisplayName("맛 원함·피함이나 특정 분류일 때 1등 분류가 OpenAI와 다르면 tasteMatch·categoryMatch가 false다")
    void writesMismatch_whenTastesOrCategoriesDiffer() {
        OpenAiSide avoidSpicy = new OpenAiSide(1L, 10L, 9, ChatIntent.RECOMMEND,
                List.of(), List.of(Taste.SPICY), List.of("국"), true);

        Map<String, String> values = values(JevComparisonLog.line(avoidSpicy,
                JevSide.finished(Optional.of(judgement(ChatIntent.RECOMMEND, true)), 500L)));

        assertThat(values).containsEntry("openaiAvoided", "SPICY").containsEntry("jevAvoided", "")
                .containsEntry("tasteMatch", "false").containsEntry("categoryMatch", "false")
                .containsEntry("openaiMealkit", "true");
    }

    @Test
    @DisplayName("요청 종류가 특정 분류가 아니면 1등 분류는 남기고 categoryMatch는 -로 남긴다")
    void writesNoneCategoryMatch_whenRequestKindIsNotCategory() {
        JevIntentJudgement broad = judgement(ChatIntent.RECOMMEND, true);
        JevIntentJudgement.Second second = broad.second();
        JevIntentJudgement withBroad = new JevIntentJudgement(broad.first(), new JevIntentJudgement.Second(
                second.dishNamed(), second.featureNamed(), second.ingredientNamed(), second.mealkitWanted(), second.tastes(),
                RequestKind.BROAD, 0.77, null, 0.6, second.call()), broad.totalLatencyMs());

        Map<String, String> values = values(JevComparisonLog.line(OPENAI, JevSide.finished(Optional.of(withBroad), 520L)));

        assertThat(values).containsEntry("jevRequestKind", "BROAD").containsEntry("jevRequestKindConfidence", "0.77")
                .containsEntry("jevCategory", "-").containsEntry("jevCategoryConfidence", "0.6")
                .containsEntry("categoryMatch", "-");
    }

    // 1차: 의도 0.97, 묻는 말 0.05. 2차: 매운맛 원함, 특정 분류 0.88, 1등 분류 찌개 0.81, 밀키트 0.08
    static JevIntentJudgement judgement(ChatIntent intent, boolean withSecond) {
        Map<ChatIntent, Double> probabilities = new EnumMap<>(ChatIntent.class);
        Arrays.stream(ChatIntent.values()).forEach(each -> probabilities.put(each, each == intent ? 0.98 : 0.0));
        JevIntentJudgement.First first = new JevIntentJudgement.First(intent, intent, 0.97, probabilities, 0.05, FridgeScope.ALL, 0.9,
                new JevIntentJudgement.Call("jev-1.14.0", "req_first", 210L, 417));
        if (!withSecond) {
            return new JevIntentJudgement(first, null, 230L);
        }
        Map<Taste, TasteAnswer> tastes = new EnumMap<>(Taste.class);
        Arrays.stream(Taste.values()).forEach(taste -> tastes.put(taste,
                new TasteAnswer(taste == Taste.SPICY ? TasteStance.WANTED : TasteStance.NOT_MENTIONED, 0.9)));
        JevIntentJudgement.Second second = new JevIntentJudgement.Second(0.09, 0.74, 0.07, 0.08, tastes,
                RequestKind.CATEGORY, 0.88, "찌개", 0.81,
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

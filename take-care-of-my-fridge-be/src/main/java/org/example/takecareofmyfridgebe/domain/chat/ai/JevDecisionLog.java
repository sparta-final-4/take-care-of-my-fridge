package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * "상담 의도 Jev 결정" 로그 한 줄. 상담 의도를 Jev로 정했는지(AI가 실패했으면 어떻게 했는지)와 Jev 판단을 남긴다.
 * 감사 페이지에서 읽을 수 있게 칸 이름과 순서를 고정한 key=value 형식이다.
 * <ul>
 *   <li>값이 없으면(Jev 실패, 2차를 묻지 않음) {@value #NONE}, 빈 목록은 빈 값(finalWanted=)</li>
 *   <li>목록은 쉼표로 잇는다. 맛은 Member.Taste 순서, 분류는 정한 순서</li>
 *   <li>Jev 분류는 요청 종류(jevRequestKind)와 1등 분류(jevCategory, "없음"이 1등이면 -)·확률, 2등 분류(jevCategory2, "없음"일 수 있음)·확률로 남긴다</li>
 *   <li>decision은 상담 의도를 무엇으로 정했는지(JevDecision.Decision), jevUsedKind·jevUsedValues는 Jev로 정한 추천에서
 *       쓴 요청 종류와 값(특정 요리면 요리 이름, 특정 분류면 분류, 넓은 요청이면 요리 이름 목록),
 *       jevUsedReason은 Jev 요청 종류나 OpenAI 요리 이름을 바꾼 까닭(JevDecision.Reason. 메시지의 키워드를 요리 이름으로 주웠으면 DISH_FROM_KEYWORD),
 *       selfHarmBy는 자해 안내를 보낸 조건(JevDecision.SelfHarmBy)</li>
 *   <li>finalFollows는 최종 이어짐(직전 조건을 이어받은 추천인지, 추천이 아니면 false), jevFollowsPrevious는 Jev가 답한 직전 추천에 이어지는 말일 확률
 *       (직전 추천이 없어 묻지 않았으면 -). Jev로 정했으면 추천이고 이 확률이 기준(JevIntentClassifier.MIN_FOLLOWS_PREVIOUS) 이상일 때 이어짐이다</li>
 *   <li>final… 칸은 상담 최종 결과다(서비스 규칙·알레르기 반영 후)</li>
 *   <li>메시지 원문·직전 추천 글·넓은 요청 표현·키는 넣지 않는다</li>
 * </ul>
 */
final class JevDecisionLog {

    static final String NAME = "상담 의도 Jev 결정";
    // 밀키트 Noul을 "예"로 볼 기준. 임시 기준이다 (평가에서 정함)
    static final double YES_THRESHOLD = 0.5;
    static final String NONE = "-";

    enum Status {
        OK,
        // 1차 호출·답 읽기 실패 (2차도 없다)
        FIRST_FAILED,
        // 1차는 됐지만 물어야 할 2차가 실패
        SECOND_FAILED,
        // 부르지 않음 (키 없음, 실행기 자리 없음)
        SKIPPED
    }

    /**
     * 상담 최종 결과 (서비스 규칙·알레르기 반영 후).
     *
     * @param follows 이어짐 (직전 조건을 이어받은 추천인지)
     * @param mealkit 밀키트만 보여 줄 추천인지 (추천이고 밀키트 단어가 부정 없이 있음, ChatRecommendationFacade와 같은 기준)
     */
    record FinalSide(
            Long memberId,
            Long sessionId,
            int messageLength,
            ChatIntent intent,
            boolean follows,
            List<Member.Taste> wanted,
            List<Member.Taste> avoided,
            List<String> categories,
            boolean mealkit
    ) {

        static FinalSide of(Long memberId, Long sessionId, int messageLength, ChatIntentResolution resolution, boolean mealkit) {
            RecipeQuery query = resolution.recipeQuery();
            return new FinalSide(memberId, sessionId, messageLength, resolution.intent(), resolution.follows(),
                    query == null ? List.of() : query.wantedTastes(),
                    query == null ? List.of() : query.avoidTastes(),
                    query == null ? List.of() : query.moodCategories(),
                    mealkit);
        }
    }

    /**
     * Jev 쪽 결과.
     *
     * @param judgement 판단 결과. 1차가 실패했거나 건너뛰었으면 빈 값
     * @param latencyMs Jev 작업 시작부터 끝까지 (건너뛰었으면 null)
     */
    record JevSide(Status status, Optional<JevIntentJudgement> judgement, Long latencyMs) {

        static JevSide skipped() {
            return new JevSide(Status.SKIPPED, Optional.empty(), null);
        }

        // Jev 작업이 예외로 끝남 (분류기는 예외를 내지 않지만 막아 둔다)
        static JevSide failed() {
            return new JevSide(Status.FIRST_FAILED, Optional.empty(), null);
        }

        static JevSide finished(Optional<JevIntentJudgement> judgement, long latencyMs) {
            Status status = judgement.map(found -> JevIntentClassifier.asksSecond(found.first().intent()) && !found.hasSecond()
                            ? Status.SECOND_FAILED : Status.OK)
                    .orElse(Status.FIRST_FAILED);
            return new JevSide(status, judgement, latencyMs);
        }
    }

    private JevDecisionLog() {
    }

    static String line(FinalSide result, JevSide jev, JevDecision decision) {
        JevIntentJudgement.First first = jev.judgement().map(JevIntentJudgement::first).orElse(null);
        JevIntentJudgement.Second second = jev.judgement().map(JevIntentJudgement::second).orElse(null);
        Set<Member.Taste> jevWanted = second == null ? null : jevTastes(second, TasteStance.WANTED);
        Set<Member.Taste> jevAvoided = second == null ? null : jevTastes(second, TasteStance.AVOIDED);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("memberId", result.memberId());
        fields.put("sessionId", result.sessionId());
        fields.put("messageLength", result.messageLength());
        fields.put("finalIntent", result.intent());
        fields.put("finalFollows", result.follows());
        fields.put("jevIntent", first == null ? null : first.intent());
        fields.put("jevIntentConfidence", first == null ? null : first.intentConfidence());
        fields.put("jevFollowsPrevious", first == null ? null : first.followsPrevious());
        fields.put("finalWanted", join(tastes(result.wanted())));
        fields.put("finalAvoided", join(tastes(result.avoided())));
        fields.put("jevWanted", jevWanted == null ? null : join(jevWanted));
        fields.put("jevAvoided", jevAvoided == null ? null : join(jevAvoided));
        fields.put("finalCategories", join(result.categories()));
        fields.put("jevRequestKind", second == null ? null : second.requestKind());
        fields.put("jevRequestKindConfidence", second == null ? null : second.requestKindConfidence());
        fields.put("jevCategory", second == null ? null : second.category());
        fields.put("jevCategoryConfidence", second == null ? null : second.categoryConfidence());
        fields.put("jevCategory2", second == null ? null : second.secondCategory());
        fields.put("jevCategory2Confidence", second == null ? null : second.secondCategoryConfidence());
        fields.put("finalMealkit", result.mealkit());
        fields.put("jevMealkit", second == null ? null : second.mealkitWanted() >= YES_THRESHOLD);
        fields.put("jevAsksQuestion", first == null ? null : first.asksQuestion());
        fields.put("jevDishNamed", second == null ? null : second.dishNamed());
        fields.put("jevModel", first == null ? null : first.call().model());
        fields.put("jevRequestIds", first == null ? null : pair(first.call().requestId(), second == null ? null : second.call().requestId()));
        fields.put("jevLatencyMs", first == null && jev.latencyMs() == null ? null
                : (first == null ? NONE : first.call().latencyMs()) + "/" + (second == null ? NONE : second.call().latencyMs())
                + "/" + (jev.latencyMs() == null ? NONE : jev.latencyMs()));
        fields.put("jevInputTokens", first == null ? null
                : pair(first.call().inputTokens(), second == null ? null : second.call().inputTokens()));
        fields.put("jevStatus", jev.status());
        fields.put("decision", decision.decision());
        fields.put("jevUsedKind", decision.usedKind());
        // 한 줄을 공백으로 나눠 읽으므로 이름 안의 공백은 뺀다 ("토마토 스파게티" → 토마토스파게티)
        fields.put("jevUsedValues", decision.usedKind() == null ? null
                : join(decision.usedValues().stream().map(ChatTexts::removeWhitespace).toList()));
        fields.put("jevUsedReason", decision.usedKind() == null ? null : decision.reason());
        // 자해 안내를 보낸 조건 (JEV_TOP_INTENT·JEV_PROBABILITY·OPENAI_RISK). 자해 안내가 아니면 -
        fields.put("selfHarmBy", decision.selfHarmBy());

        return NAME + " - " + fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue() == null ? NONE : entry.getValue()))
                .collect(Collectors.joining(" "));
    }

    private static Set<Member.Taste> tastes(Collection<Member.Taste> tastes) {
        return new TreeSet<>(tastes == null ? List.of() : tastes);
    }

    private static Set<Member.Taste> jevTastes(JevIntentJudgement.Second second, TasteStance stance) {
        return second.tastes().entrySet().stream()
                .filter(entry -> entry.getValue().stance() == stance)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String join(Collection<?> values) {
        return values == null ? "" : values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String pair(Object first, Object second) {
        return (first == null ? NONE : first) + "/" + (second == null ? NONE : second);
    }
}

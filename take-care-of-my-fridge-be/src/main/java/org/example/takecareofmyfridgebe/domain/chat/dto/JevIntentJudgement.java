package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;
import java.util.Map;

/**
 * Jev 의도 판단 결과. 1차는 모든 메시지, 2차는 1차 의도가 RECOMMEND·REFINE일 때만 묻는다.
 * 믿을지는 Choice의 confidence(가장 큰 확률과 다를 수 있다), Noul의 "예" 확률로 판단한다.
 * 분류 고르기 규칙(CategoryRule)만 여기 두고, 의도·맛을 자르는 기준값은 여기서 정하지 않는다.
 *
 * @param first          1차 답
 * @param second         2차 답. 묻지 않았거나 2차 호출이 실패했으면 null
 * @param totalLatencyMs 1차부터 2차(실패 포함)까지 걸린 시간
 */
public record JevIntentJudgement(
        First first,
        Second second,
        long totalLatencyMs
) {

    public boolean hasSecond() {
        return second != null;
    }

    /**
     * 1차: 의도, 정보를 묻는 말인지, 냉장고 보기 범위.
     *
     * @param intentProbabilities 의도별 확률 (ChatIntent 순서)
     * @param asksQuestion        정보를 묻는 말일 확률 (Noul)
     * @param fridgeScope         냉장고 보기 범위. FRIDGE가 아닐 때도 답이 온다
     */
    public record First(
            ChatIntent intent,
            double intentConfidence,
            Map<ChatIntent, Double> intentProbabilities,
            double asksQuestion,
            FridgeScope fridgeScope,
            double fridgeScopeConfidence,
            Call call
    ) {
    }

    /**
     * 2차: 추천 조건. 확률은 모두 Noul "예" 확률이다.
     *
     * @param tastes     맛별 답 (Member.Taste 순서)
     * @param categories 분류 이름 → 이 요청에 그 분류가 어울릴 확률 (넘긴 분류 순서). 고르기는 CategoryRule
     */
    public record Second(
            double dishNamed,
            double featureNamed,
            double ingredientNamed,
            double mealkitWanted,
            Map<Member.Taste, TasteAnswer> tastes,
            Map<String, Double> categories,
            Call call
    ) {

        // 분류 확률을 기본 규칙(CategoryRule.DEFAULT)으로 고른 것
        public List<String> pickedCategories() {
            return CategoryRule.DEFAULT.pick(categories);
        }
    }

    /**
     * 분류마다 "이 요청에 어울리나" Noul 확률에서 고를 분류를 정하는 규칙. 요청이 좁으면 1등 근처만, 넓으면 상위 몇 개를 고른다.
     * <ul>
     *   <li>1등 확률이 top 이상 (좁은 요청, "김치찌개"): 1등 + 1등과 차이가 gap 이내인 분류</li>
     *   <li>1등도 top 미만 (넓은 요청, "술안주"): 확률이 floor 이상인 상위 widest개</li>
     *   <li>모든 분류가 floor 미만 (분류가 없는 요청): 빈 목록</li>
     * </ul>
     * 기준값(DEFAULT)은 평가(JevIntentEvalManualTest, chat-intent-eval.yml)로 정한다. 시작값은 top 0.7, gap 0.15, floor 0.2, widest 4.
     *
     * @param top    좁은 요청으로 볼 1등 확률
     * @param gap    좁은 요청에서 1등과 함께 고를 확률 차이
     * @param floor  고를 수 있는 가장 낮은 확률
     * @param widest 넓은 요청에서 고를 분류 수
     */
    public record CategoryRule(double top, double gap, double floor, int widest) {

        public static final CategoryRule DEFAULT = new CategoryRule(0.7, 0.15, 0.2, 4);
        // 0.85 - 0.7처럼 소수 뺄셈 오차로 차이가 gap을 아주 조금 넘는 것을 막는다
        private static final double EPSILON = 1e-9;

        /**
         * @param probabilities 분류 이름 → 확률
         * @return 고른 분류 (확률 높은 순, 같으면 넘긴 순서)
         */
        public List<String> pick(Map<String, Double> probabilities) {
            List<Map.Entry<String, Double>> ranked = probabilities.entrySet().stream()
                    .filter(entry -> entry.getValue() >= floor - EPSILON)
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .toList();
            if (ranked.isEmpty()) {
                return List.of();
            }
            double best = ranked.get(0).getValue();
            if (best >= top - EPSILON) {
                return ranked.stream()
                        .filter(entry -> best - entry.getValue() <= gap + EPSILON)
                        .map(Map.Entry::getKey)
                        .toList();
            }
            return ranked.stream().limit(widest).map(Map.Entry::getKey).toList();
        }
    }

    public record TasteAnswer(
            TasteStance stance,
            double confidence
    ) {
    }

    public enum TasteStance {
        WANTED,
        AVOIDED,
        NOT_MENTIONED
    }

    /**
     * 호출 하나의 기록.
     *
     * @param model       실제로 답한 모델 (응답 model)
     * @param requestId   응답 헤더 x-typesafe-request-id
     * @param latencyMs   이 호출에 걸린 시간
     * @param inputTokens 과금 입력 토큰 (응답에 없으면 null)
     */
    public record Call(
            String model,
            String requestId,
            long latencyMs,
            Integer inputTokens
    ) {
    }
}

package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Map;

/**
 * Jev 의도 판단 결과. 1차는 모든 메시지, 2차는 1차 의도가 RECOMMEND·REFINE일 때만 묻는다.
 * 믿을지는 Choice의 confidence(가장 큰 확률과 다를 수 있다), Noul의 "예" 확률로 판단한다.
 * 자르는 기준값은 여기서 정하지 않는다.
 *
 * @param first         1차 답
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
     * 2차: 추천 조건. 요리 이름·특징·재료·밀키트는 Noul "예" 확률이다.
     *
     * @param tastes                맛별 답 (Member.Taste 순서)
     * @param requestKind           요청 종류 (특정 요리 / 특정 분류 / 넓은 요청 / 없음)
     * @param requestKindConfidence 요청 종류 Choice의 confidence
     * @param category              1등 분류. "없음"이 1등이면 null. 요청 종류가 특정 분류일 때만 의미가 있다
     * @param categoryConfidence    분류 Choice의 confidence
     */
    public record Second(
            double dishNamed,
            double featureNamed,
            double ingredientNamed,
            double mealkitWanted,
            Map<Member.Taste, TasteAnswer> tastes,
            RequestKind requestKind,
            double requestKindConfidence,
            String category,
            double categoryConfidence,
            Call call
    ) {
    }

    /**
     * 요청 종류. 어떻게 레시피를 찾을지 가른다 (상담 흐름 연결은 아직 하지 않았다).
     */
    public enum RequestKind {
        // 요리 하나의 이름 ("김치찌개") → 요리 이름 검색
        DISH,
        // 요리 종류 하나 ("찌개", "디저트") → 1등 분류로 검색
        CATEGORY,
        // 상황·쓰임새·여러 종류에 걸친 말 ("술안주", "국물") → LLM이 준 요리 이름으로 검색
        BROAD,
        // 요리 종류나 이름을 말하지 않음 ("뭐 먹지?") → 냉장고 기반
        NONE
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

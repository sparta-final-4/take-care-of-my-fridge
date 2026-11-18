package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;

import java.util.List;

/**
 * 상담 의도를 무엇으로 정했는지 (Jev 비교 로그에 남긴다).
 *
 * @param decision    정한 방법
 * @param usedKind    Jev로 정한 추천에서 쓴 요청 종류 (경계 규칙 적용 뒤). 그 밖이면 null
 * @param usedValues  그 요청 종류로 쓴 값: 특정 요리면 요리 이름, 특정 분류면 분류, 넓은 요청이면 요리 이름 목록. 없으면 빈 목록
 * @param reason      Jev 요청 종류나 OpenAI 요리 이름을 그대로 쓰지 않고 바꾼 까닭 (바꾸지 않았으면 NONE)
 * @param selfHarmBy  자해 안내를 보낸 조건. 자해 안내가 아니면 null
 */
public record JevDecision(
        Decision decision,
        RequestKind usedKind,
        List<String> usedValues,
        Reason reason,
        SelfHarmBy selfHarmBy
) {

    public JevDecision {
        usedValues = usedValues == null ? List.of() : List.copyOf(usedValues);
        reason = reason == null ? Reason.NONE : reason;
    }

    public JevDecision(Decision decision, RequestKind usedKind, List<String> usedValues, Reason reason) {
        this(decision, usedKind, usedValues, reason, null);
    }

    public JevDecision(Decision decision, RequestKind usedKind, List<String> usedValues) {
        this(decision, usedKind, usedValues, Reason.NONE);
    }

    public static JevDecision of(Decision decision) {
        return new JevDecision(decision, null, List.of());
    }

    // 자해 안내로 정한 결정
    public static JevDecision selfHarm(Decision decision, SelfHarmBy by) {
        return new JevDecision(decision, null, List.of(), Reason.NONE, by);
    }

    /**
     * Jev 요청 종류나 OpenAI 요리 이름을 바꾼 까닭 (비교 로그 jevUsedReason).
     */
    public enum Reason {
        // 바꾸지 않음
        NONE,
        // 특정 요리인데 OpenAI 요리 이름이 비어, 메시지에 글자 그대로 있는 OpenAI 키워드를 요리 이름으로 주움
        DISH_FROM_KEYWORD,
        // 요리 이름이 분류 이름과 같아(죽) 요리 이름으로 쓰지 않고 그 분류로 찾음
        DISH_IS_CATEGORY,
        // 특정 요리인데 요리 이름이 없어 Jev 1등 분류로 찾음
        DISH_TO_TOP_CATEGORY,
        // 1등 분류 확률이 기준 미만이라 분류를 쓰지 않음 (특정 요리는 분류 없이, 특정 분류·요리 이름 없음은 넓은 요청)
        LOW_CATEGORY_CONFIDENCE,
        // 특정 분류인데 1등 분류가 없음이라 넓은 요청(또는 밀키트면 없음)
        NO_TOP_CATEGORY,
        // 특정 요리인데 요리 이름도 쓸 분류도 없어 넓은 요청
        NO_DISH
    }

    public enum Decision {
        // Jev 판단으로 정함 (confidence와 상관없이 Jev 1등 의도). Jev 1차 자해 판단으로 자해 안내를 보낸 경우도 여기다
        JEV,
        // Jev가 실패하거나(1차·필요한 2차), 제한 시간 안에 끝나지 않거나, 건너뛰어(키 없음 등) 서버 답으로 정함
        JEV_FAILED_SERVER_FALLBACK,
        // Jev 1차가 실패해 OpenAI 꺼내기의 자해 뜻(selfHarmRisk)으로 자해 안내를 보냄
        JEV_FAILED_OPENAI_SELF_HARM,
        // Jev가 꺼낸 값이 필요한 의도(추천·레시피 자세히)로 정했는데 OpenAI 꺼내기가 실패해 서버 답으로 정함
        OPENAI_FAILED_SERVER_FALLBACK
    }

    /**
     * 자해 안내를 보낸 조건 (비교 로그 selfHarmBy). 단어 차단(SafetyWordChecker)은 이 전에 따로 돈다.
     */
    public enum SelfHarmBy {
        // Jev 1차 1등 의도가 SELF_HARM
        JEV_TOP_INTENT,
        // Jev 1차 자해 확률이 기준(ChatIntentFacade.SELF_HARM_MIN_PROBABILITY) 이상
        JEV_PROBABILITY,
        // Jev 1차가 실패해 OpenAI 꺼내기의 selfHarmRisk가 true
        OPENAI_RISK
    }
}

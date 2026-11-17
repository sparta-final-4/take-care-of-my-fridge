package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;

import java.util.List;

/**
 * 상담 의도를 무엇으로 정했는지 (Jev 비교 로그에 남긴다).
 *
 * @param decision   정한 방법
 * @param usedKind   Jev로 정했을 때(또는 되돌림에서 넓은 요청 경로를 탔을 때) 쓴 요청 종류 (경계 규칙 적용 뒤). 그 밖이면 null
 * @param usedValues 그 요청 종류로 쓴 값: 특정 요리면 요리 이름, 특정 분류면 분류, 넓은 요청이면 요리 이름 목록. 없으면 빈 목록
 * @param reason     Jev 요청 종류나 OpenAI 요리 이름을 그대로 쓰지 않고 바꾼 까닭 (바꾸지 않았으면 NONE)
 */
public record JevDecision(
        Decision decision,
        RequestKind usedKind,
        List<String> usedValues,
        Reason reason
) {

    public static final JevDecision SHADOW = new JevDecision(Decision.SHADOW, null, List.of());

    public JevDecision {
        usedValues = usedValues == null ? List.of() : List.copyOf(usedValues);
        reason = reason == null ? Reason.NONE : reason;
    }

    public JevDecision(Decision decision, RequestKind usedKind, List<String> usedValues) {
        this(decision, usedKind, usedValues, Reason.NONE);
    }

    public static JevDecision of(Decision decision) {
        return new JevDecision(decision, null, List.of());
    }

    /**
     * Jev 요청 종류나 OpenAI 요리 이름을 바꾼 까닭 (비교 로그 jevUsedReason).
     */
    public enum Reason {
        // 바꾸지 않음
        NONE,
        // 특정 요리인데 OpenAI 요리 이름이 비어, 메시지에 글자 그대로 있는 OpenAI 키워드를 요리 이름으로 주움
        DISH_FROM_KEYWORD,
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
        // 그림자 모드: OpenAI 결과로 정하고 Jev는 비교만
        SHADOW,
        // on: Jev 판단으로 정함
        JEV,
        // on: Jev 의도 confidence가 기준값보다 낮아 OpenAI 결과로 정함
        OPENAI_LOW_CONFIDENCE,
        // on: Jev 의도 confidence가 낮아 OpenAI 의도(추천 계열)를 쓰되, Jev 2차 요청 종류가 넓은 요청이라 넓은 요청 요리 이름으로 찾음
        OPENAI_LOW_CONFIDENCE_JEV_BROAD,
        // on: Jev가 실패하거나(1차·필요한 2차), 제한 시간 안에 끝나지 않거나, 건너뛰어 OpenAI 결과로 정함
        OPENAI_JEV_FAILED,
        // on: OpenAI 의도 판단이 실패해 지금 실패 처리(선택 재료 유무)로 정함
        OPENAI_FAILED
    }
}

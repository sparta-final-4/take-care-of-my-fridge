package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;

import java.util.List;

/**
 * 상담 의도를 무엇으로 정했는지 (Jev 비교 로그에 남긴다).
 *
 * @param decision   정한 방법
 * @param usedKind   Jev로 정했을 때 쓴 요청 종류 (경계 규칙 적용 뒤). 그 밖이면 null
 * @param usedValues 그 요청 종류로 쓴 값: 특정 요리면 요리 이름, 특정 분류면 분류, 넓은 요청이면 요리 이름 목록. 없으면 빈 목록
 */
public record JevDecision(
        Decision decision,
        RequestKind usedKind,
        List<String> usedValues
) {

    public static final JevDecision SHADOW = new JevDecision(Decision.SHADOW, null, List.of());

    public JevDecision {
        usedValues = usedValues == null ? List.of() : List.copyOf(usedValues);
    }

    public static JevDecision of(Decision decision) {
        return new JevDecision(decision, null, List.of());
    }

    public enum Decision {
        // 그림자 모드: OpenAI 결과로 정하고 Jev는 비교만
        SHADOW,
        // on: Jev 판단으로 정함
        JEV,
        // on: Jev 의도 confidence가 기준값보다 낮아 OpenAI 결과로 정함
        OPENAI_LOW_CONFIDENCE,
        // on: Jev가 실패하거나(1차·필요한 2차), 제한 시간 안에 끝나지 않거나, 건너뛰어 OpenAI 결과로 정함
        OPENAI_JEV_FAILED,
        // on: OpenAI 의도 판단이 실패해 지금 실패 처리(선택 재료 유무)로 정함
        OPENAI_FAILED
    }
}

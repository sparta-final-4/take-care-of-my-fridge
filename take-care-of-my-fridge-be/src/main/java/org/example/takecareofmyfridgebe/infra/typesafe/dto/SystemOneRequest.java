package org.example.takecareofmyfridgebe.infra.typesafe.dto;

import java.util.Map;

// POST /v1/systemone 본문. questions는 질문 이름 → 질문이고, 답도 같은 이름으로 온다
public record SystemOneRequest(
        String model,
        String state,
        Map<String, SystemOneQuestion> questions
) {
}

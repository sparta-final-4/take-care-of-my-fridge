package org.example.takecareofmyfridgebe.infra.typesafe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /v1/systemone 질문 하나. Choice는 criteria에 선택지 이름 → 설명을 넣고, Noul은 질문 글만 넣는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemOneQuestion(
        String type,
        String instructions,
        Map<String, String> criteria
) {

    // 선택지 순서를 그대로 보낸다
    public static SystemOneQuestion choice(String instructions, Map<String, String> criteria) {
        return new SystemOneQuestion("choice", instructions, Collections.unmodifiableMap(new LinkedHashMap<>(criteria)));
    }

    public static SystemOneQuestion noul(String instructions) {
        return new SystemOneQuestion("noul", instructions, null);
    }
}

package org.example.takecareofmyfridgebe.infra.typesafe.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * POST /v1/systemone 응답. model은 실제로 답한 버전(예: jev-1.14.0)이다.
 * Choice 답은 choice·confidence·probabilities, Noul 답은 noul("예" 확률)만 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemOneResponse(
        String model,
        Map<String, Answer> answers,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(
            String type,
            String choice,
            Double confidence,
            Map<String, Double> probabilities,
            Double noul
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {
    }
}

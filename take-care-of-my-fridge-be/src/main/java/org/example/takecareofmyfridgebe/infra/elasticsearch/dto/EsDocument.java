package org.example.takecareofmyfridgebe.infra.elasticsearch.dto;

import java.util.Map;

public record EsDocument(
        long id,
        Map<String, Object> source
) {

    public EsDocument {
        if (source == null) {
            throw new IllegalArgumentException("source 값은 반드시 정의해야 합니다.");
        }
    }
}


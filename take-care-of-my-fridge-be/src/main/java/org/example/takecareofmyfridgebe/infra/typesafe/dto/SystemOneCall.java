package org.example.takecareofmyfridgebe.infra.typesafe.dto;

/**
 * 성공한 호출 하나.
 *
 * @param requestId 응답 헤더 x-typesafe-request-id (없으면 null)
 * @param latencyMs 요청을 보내고 응답 본문을 다 읽기까지 걸린 시간
 */
public record SystemOneCall(
        SystemOneResponse response,
        String requestId,
        long latencyMs
) {

    public Integer inputTokens() {
        return response.usage() == null ? null : response.usage().inputTokens();
    }
}

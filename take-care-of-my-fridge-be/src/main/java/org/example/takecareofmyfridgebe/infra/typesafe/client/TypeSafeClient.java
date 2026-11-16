package org.example.takecareofmyfridgebe.infra.typesafe.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneCall;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneQuestion;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneRequest;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * TypeSafe System One(Jev) 호출. 재시도하지 않고, 실패하면 예외 대신 빈 값과 WARN 로그를 남긴다.
 * 본문은 UTF-8로 보낸다(한글이 다른 문자셋으로 가면 "There was an error parsing the body"가 난다).
 * 로그에는 state 원문·키·응답 본문을 쓰지 않는다(422 본문에는 state가 다시 담겨 온다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TypeSafeClient {

    static final String SYSTEM_ONE_PATH = "/v1/systemone";
    static final String REQUEST_ID_HEADER = "x-typesafe-request-id";
    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
    private static final String NO_STATUS = "-";

    private final RestClient typeSafeRestClient;
    private final TypeSafeProperties properties;

    /**
     * 질문을 한 요청으로 묻는다. 키가 없으면 부르지 않고 빈 값이다. 어느 기능에서 쓸지(켜고 끄기)는 부르는 쪽이 정한다.
     *
     * @param purpose   로그에 남길 호출 이름 (예: "의도 1차")
     * @param state     질문들이 가리키는 글
     * @param questions 질문 이름 → 질문
     * @return 성공한 호출. 타임아웃·HTTP 오류(401·403·422·429·5xx·크레딧 부족 등)·응답 형식 오류면 빈 값
     */
    public Optional<SystemOneCall> ask(String purpose, String state, Map<String, SystemOneQuestion> questions) {
        if (!ready()) {
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<SystemOneResponse> response = typeSafeRestClient.post()
                    .uri(SYSTEM_ONE_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(JSON_UTF8)
                    .body(new SystemOneRequest(properties.model(), state, questions))
                    .retrieve()
                    .toEntity(SystemOneResponse.class);
            String requestId = response.getHeaders().getFirst(REQUEST_ID_HEADER);
            if (response.getBody() == null) {
                warn(purpose, String.valueOf(response.getStatusCode().value()), requestId, state, startedAt, "빈 응답 본문");
                return Optional.empty();
            }
            return Optional.of(new SystemOneCall(response.getBody(), requestId, elapsedMs(startedAt)));
        } catch (RestClientResponseException e) {
            HttpHeaders headers = e.getResponseHeaders();
            String requestId = headers == null ? null : headers.getFirst(REQUEST_ID_HEADER);
            warn(purpose, String.valueOf(e.getStatusCode().value()), requestId, state, startedAt, e.getClass().getSimpleName());
        } catch (RestClientException e) {
            // 타임아웃·연결 실패(ResourceAccessException), 응답 JSON 해석 실패
            warn(purpose, NO_STATUS, null, state, startedAt, e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    // 키가 있어 부를 수 있는지
    public boolean ready() {
        return properties.hasApiKey();
    }

    // 호출 하나의 제한 시간 (app.typesafe.timeout)
    public Duration timeout() {
        return properties.timeout();
    }

    private void warn(String purpose, String status, String requestId, String state, long startedAt, String cause) {
        log.warn("Jev {} 호출 실패 - status: {}, requestId: {}, 글 길이: {}, 걸린 시간: {}ms, 원인: {}",
                purpose, status, requestId, state.length(), elapsedMs(startedAt), cause);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ForecastWorkerClient {

    private final RestClient client;

    public ForecastWorkerClient(@Value("${forecast.worker-url:http://forecast-worker:8091}") String workerUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMinutes(5));
        // 워커는 Content-Length로 본문 크기를 확인하므로, 본문을 모아 길이를 붙여 보낸다(기본은 길이 없는 chunked 전송).
        this.client = RestClient.builder().baseUrl(workerUrl)
                .requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }

    public Result predict(LocalDate weekStart, List<Sample> samples, List<ProductHistory> products) {
        return client.post().uri("/predict").body(new Request(weekStart, samples, products))
                .retrieve().body(Result.class);
    }

    public record Sample(List<Long> history, long target) {
    }

    public record ProductHistory(Long productId, List<Long> history) {
    }

    public record Prediction(Long productId, int quantity) {
    }

    public record Result(String modelVersion, List<Prediction> predictions) {
    }

    public record Request(LocalDate weekStart, List<Sample> samples, List<ProductHistory> products) {
    }
}

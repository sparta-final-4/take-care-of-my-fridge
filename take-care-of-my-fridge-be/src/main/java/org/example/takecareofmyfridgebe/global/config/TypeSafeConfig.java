package org.example.takecareofmyfridgebe.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * TypeSafe(Jev) API 호출용 RestClient와, 상담 의도 판단에서 Jev를 OpenAI 꺼내기와 함께 다른 스레드에서 부르는 실행기.
 * RestClient 연결·읽기 제한 시간은 app.typesafe.timeout이다. 키는 기본 헤더로 넣지 않고 요청마다 넣는다(TypeSafeClient). 키가 비어도 빈은 만든다.
 */
@Configuration
public class TypeSafeConfig {

    // Jev 호출 스레드 수. 판단 하나가 0.2~0.5초(1차+2차, jev-v4 중앙값 355ms)라 스레드 4개면 초당 8건 넘게 처리한다
    static final int JEV_THREADS = 4;
    // 대기 줄. 상담은 Jev를 시작부터 3초(app.typesafe.timeout)만 기다리므로, 그 안에 시작조차 못 할 작업은 줄에 두지 않는다.
    // 스레드당 2개(약 1초 대기)까지만 두고, 넘치면 바로 Jev 실패(서버 답)로 처리한다. 예전 50은 TypeSafe가 느려지면
    // 이미 포기한 요청의 작업이 줄에 쌓여 늦게 돌았다
    static final int JEV_QUEUE_CAPACITY = 8;

    @Bean
    public RestClient typeSafeRestClient(TypeSafeProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }

    // Executor 타입 빈으로 두면 Spring Boot 기본 applicationTaskExecutor가 빠지므로 Executor가 아닌 타입으로 둔다
    @Bean(destroyMethod = "close")
    public TypeSafeExecutor typeSafeExecutor() {
        return new TypeSafeExecutor(JEV_THREADS, JEV_QUEUE_CAPACITY);
    }
}

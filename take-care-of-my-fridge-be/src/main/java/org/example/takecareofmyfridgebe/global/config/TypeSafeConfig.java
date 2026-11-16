package org.example.takecareofmyfridgebe.global.config;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatJevProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * TypeSafe(Jev) API 호출용 RestClient. 연결·읽기 제한 시간은 app.chat.jev.timeout이다.
 * 키는 기본 헤더로 넣지 않고 요청마다 넣는다(TypeSafeClient). 키가 비어도 빈은 만든다.
 */
@Configuration
public class TypeSafeConfig {

    @Bean
    public RestClient typeSafeRestClient(ChatJevProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }
}

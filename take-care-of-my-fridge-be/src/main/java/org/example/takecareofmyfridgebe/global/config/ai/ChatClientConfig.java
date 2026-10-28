package org.example.takecareofmyfridgebe.global.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 설정.
 * 레시피 후기 요약에 특화된 defaultSystem 프롬프트와 토큰 비용 로깅용 CostAdvisor를 기본 적용한다.
 * 서비스 주입 시 @Qualifier("reviewSummaryChatClient")를 명시하여 사용한다.
 */
@Configuration
public class ChatClientConfig {

    @Bean("reviewSummaryChatClient")
    public ChatClient reviewSummaryChatClient(
            ChatClient.Builder builder,
            @Value("${app.llm.input-per-1m}") double inputPer1m,
            @Value("${app.llm.output-per-1m}") double outputPer1m
    ) {
        return builder
                .defaultSystem("너는 레시피 후기를 요약하는 어시스턴트야. 후기에 있는 내용만 쓰고 지어내지 않는다.")
                .defaultAdvisors(new CostAdvisor(inputPer1m, outputPer1m, 100))
                .build();
    }
}

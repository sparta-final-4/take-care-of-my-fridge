package org.example.takecareofmyfridgebe.global.config.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ChatClientConfigTest {

    // 테스트용 가짜 키 (실제 키가 아니다)
    private static final String FAKE_KEY = "fake-test-key-0000";

    @Test
    @DisplayName("상담 꺼내기 모델은 따로 받은 키에 시간 제한 10초·재시도 없음과 꺼내기 옵션을 두고, 기본 모델(다른 호출)의 60초·재시도 1번은 그대로 둔다")
    void buildsIntentModelWithTenSecondsAndNoRetry_withoutChangingBaseModel() {
        // application.yml spring.ai.openai와 같은 기본 모델 옵션 (timeout 60s, max-retries 1).
        // 자동 설정은 키를 OpenAI 클라이언트에만 넣으므로 옵션에는 키가 없다
        OpenAiChatOptions baseOptions = OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.3)
                .timeout(Duration.ofSeconds(60)).maxRetries(1).build();

        OpenAiChatOptions intent = ChatClientConfig.intentChatModel(baseOptions, FAKE_KEY).getOptions();

        assertThat(intent.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(intent.getMaxRetries()).isZero();
        assertThat(intent.getApiKey()).isEqualTo(FAKE_KEY);
        assertThat(intent.getModel()).isEqualTo(ChatClientConfig.CHAT_INTENT_MODEL);
        assertThat(intent.getTemperature()).isEqualTo(ChatClientConfig.CHAT_INTENT_TEMPERATURE);
        assertThat(intent.getMaxTokens()).isEqualTo(ChatClientConfig.CHAT_INTENT_MAX_TOKENS);
        assertThat(baseOptions.getApiKey()).isNull();
        assertThat(baseOptions.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(baseOptions.getMaxRetries()).isEqualTo(1);
        assertThat(baseOptions.getTemperature()).isEqualTo(0.3);
    }
}

package org.example.takecareofmyfridgebe.global.config.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CostAdvisorTest {

    private final double inputPer1m = 0.15;
    private final double outputPer1m = 0.60;
    private final int order = 100;
    private final CostAdvisor costAdvisor = new CostAdvisor(new LlmCostCalculator(inputPer1m, outputPer1m), order);

    @Test
    @DisplayName("before는 전달받은 ChatClientRequest를 그대로 반환한다")
    void beforePassesThroughRequest() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        AdvisorChain chain = mock(AdvisorChain.class);

        ChatClientRequest result = costAdvisor.before(request, chain);

        assertSame(request, result);
    }

    @Test
    @DisplayName("after는 Usage가 존재하면 비용을 계산하고 응답을 그대로 반환한다")
    void afterCalculatesCostWhenUsagePresent() {
        // Given
        ChatClientResponse response = mock(ChatClientResponse.class);
        AdvisorChain chain = mock(AdvisorChain.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = new AssistantMessage("테스트 응답");

        when(response.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(1000);
        when(usage.getCompletionTokens()).thenReturn(500);
        when(usage.getTotalTokens()).thenReturn(1500);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);

        // When
        ChatClientResponse result = costAdvisor.after(response, chain);

        // Then
        assertSame(response, result);
    }

    @Test
    @DisplayName("after는 사용량이 없으면(EmptyUsage) 계산 없이 응답을 그대로 반환한다")
    void afterPassesThroughWhenUsageEmpty() {
        // Given
        ChatClientResponse response = mock(ChatClientResponse.class);
        AdvisorChain chain = mock(AdvisorChain.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);

        when(response.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(new EmptyUsage());

        // When
        ChatClientResponse result = costAdvisor.after(response, chain);

        // Then
        assertSame(response, result);
    }

    @Test
    @DisplayName("after는 chatResponse가 null이면 계산 없이 응답을 그대로 반환한다")
    void afterPassesThroughWhenChatResponseNull() {
        // Given
        ChatClientResponse response = mock(ChatClientResponse.class);
        AdvisorChain chain = mock(AdvisorChain.class);
        when(response.chatResponse()).thenReturn(null);

        // When
        ChatClientResponse result = costAdvisor.after(response, chain);

        // Then
        assertSame(response, result);
    }

    @Test
    @DisplayName("getOrder는 설정된 order 값을 반환한다")
    void getOrderReturnsConfiguredOrder() {
        assertEquals(100, costAdvisor.getOrder());
    }
}

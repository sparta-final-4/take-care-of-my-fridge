package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChatIntentExtractorTest {

    private static final String PROMPT = "규칙 {예시}는 그대로 둔다. 분류 목록: %CATEGORIES%";
    private static final String RECOMMEND_JSON = """
            {"intent":"RECOMMEND","wantedTastes":["SPICY"],"moodCategories":["국"],"maxMinutes":30,
             "includeIngredients":["두부"],"excludeIngredients":null,"useSelectedOnly":true,
             "keywords":["냉면"],"dishText":"냉면"}
            """;

    private CapturingChatModel chatModel;
    private OpenAiChatIntentExtractor extractor;

    @BeforeEach
    void setUp() {
        chatModel = new CapturingChatModel();
        extractor = new OpenAiChatIntentExtractor(
                ChatClient.builder(chatModel).build(),
                new LlmCostCalculator(0.15, 0.60),
                new ByteArrayResource(PROMPT.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Spring AI 2.0.1은 템플릿 매개변수 없이 보내면 시스템·사용자 글의 중괄호를 템플릿으로 처리하지 않는다")
    void sendsBracesAsIs_whenNoTemplateParams() {
        chatModel.reply = "답";

        ChatClient.builder(chatModel).build().prompt()
                .system("시스템 {name} {}")
                .user("사용자 {food} {")
                .call()
                .content();

        assertThat(chatModel.lastPrompt.getSystemMessage().getText()).isEqualTo("시스템 {name} {}");
        assertThat(chatModel.lastPrompt.getUserMessage().getText()).isEqualTo("사용자 {food} {");
    }

    @Test
    @DisplayName("분류 목록을 넣은 시스템 프롬프트와 중괄호가 든 메시지를 그대로 보낸다")
    void sendsPromptAndMessageAsIs_whenTheyContainBraces() {
        chatModel.reply = RECOMMEND_JSON;

        extractor.extract(1L, 10L, "{두부} 로 {name} 할 수 있는 거", List.of("국", "찌개"));

        assertThat(chatModel.lastPrompt.getSystemMessage().getText())
                .isEqualTo("규칙 {예시}는 그대로 둔다. 분류 목록: 국, 찌개");
        assertThat(chatModel.lastPrompt.getUserMessage().getText()).startsWith("{두부} 로 {name} 할 수 있는 거");
    }

    @Test
    @DisplayName("응답 모양을 IntentResult의 JSON 스키마로 강제한다")
    void sendsOutputSchema() {
        chatModel.reply = RECOMMEND_JSON;

        extractor.extract(1L, 10L, "매운 국", List.of("국"));

        assertThat(chatModel.lastPrompt.getOptions()).isInstanceOf(StructuredOutputChatOptions.class);
        String schema = ((StructuredOutputChatOptions) chatModel.lastPrompt.getOptions()).getOutputSchema();
        assertThat(schema).contains("\"intent\"", "\"wantedTastes\"", "\"useSelectedOnly\"");
    }

    @Test
    @DisplayName("응답 JSON을 IntentResult로 바꾼다")
    void parsesIntentResult() {
        chatModel.reply = RECOMMEND_JSON;

        IntentResult result = extractor.extract(1L, 10L, "두부로 할 수 있는 매운 국 30분 안에", List.of("국"));

        assertThat(result).isEqualTo(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), List.of("국"), 30,
                List.of("두부"), null, true, List.of("냉면"), "냉면"));
    }

    @Test
    @DisplayName("응답 JSON이 깨지면 예외를 던진다")
    void throws_whenJsonIsBroken() {
        chatModel.reply = "{\"intent\": \"RECOMMEND\", ";

        assertThatThrownBy(() -> extractor.extract(1L, 10L, "뭐 먹지?", List.of("국")))
                .isInstanceOf(RuntimeException.class);
    }

    // 마지막으로 받은 프롬프트를 기억하고 정해 둔 글을 답하는 가짜 모델
    private static class CapturingChatModel implements ChatModel {

        private Prompt lastPrompt;
        private String reply;

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }

        @Override
        public ChatOptions getOptions() {
            return OpenAiChatOptions.builder().build();
        }
    }
}

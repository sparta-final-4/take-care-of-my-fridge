package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChatIntentExtractorTest {

    private static final String PROMPT = "규칙 {예시}는 그대로 둔다.";
    private static final String FOLLOW_UP_RULES = "[이어지는 말에서 꺼내기] 규칙";
    private static final String EXTRACTION_JSON = """
            {"maxMinutes":30,"includeIngredients":["두부"],"excludeIngredients":null,"useSelectedOnly":true,
             "keywords":["냉면"],"dishText":"냉면","preferenceText":"냉면 같은 면 요리"}
            """;
    // 꺼내기 응답 칸 (IntentExtraction 순서). 의도·이어짐·맛·분류는 Jev가 정하므로 없다
    private static final List<String> FIELDS = List.of("maxMinutes", "includeIngredients", "excludeIngredients", "useSelectedOnly",
            "keywords", "dishText", "preferenceText", "targetIndex", "targetName", "removed", "detailQuestion", "fridgeScope");

    private CapturingChatModel chatModel;
    private OpenAiChatIntentExtractor extractor;

    @BeforeEach
    void setUp() {
        chatModel = new CapturingChatModel();
        extractor = new OpenAiChatIntentExtractor(
                ChatClient.builder(chatModel).build(),
                new LlmCostCalculator(0.15, 0.60),
                new ByteArrayResource(PROMPT.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayResource(FOLLOW_UP_RULES.getBytes(StandardCharsets.UTF_8)));
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
    @DisplayName("시스템 프롬프트와 중괄호가 든 메시지를 그대로 보낸다")
    void sendsPromptAndMessageAsIs_whenTheyContainBraces() {
        chatModel.reply = EXTRACTION_JSON;

        extractor.extract(1L, 10L, "{두부} 로 {name} 할 수 있는 거", null);

        assertThat(chatModel.lastPrompt.getSystemMessage().getText()).isEqualTo(PROMPT);
        assertThat(chatModel.lastPrompt.getUserMessage().getText()).startsWith("{두부} 로 {name} 할 수 있는 거");
    }

    @Test
    @DisplayName("응답 모양을 IntentExtraction의 JSON 스키마로 강제하고, 의도·이어짐·맛·분류 칸은 넣지 않는다 (Jev가 정한다)")
    void sendsOutputSchema_withoutIntentFields() {
        chatModel.reply = EXTRACTION_JSON;

        extractor.extract(1L, 10L, "매운 국", null);

        assertThat(chatModel.lastPrompt.getOptions()).isInstanceOf(StructuredOutputChatOptions.class);
        String schema = ((StructuredOutputChatOptions) chatModel.lastPrompt.getOptions()).getOutputSchema();
        assertThat(schema).contains("\"useSelectedOnly\"", "\"dishText\"", "\"targetIndex\"", "\"removed\"", "\"fridgeScope\"")
                .doesNotContain("\"intent\"", "\"followsPrevious\"", "\"wantedTastes\"", "\"avoidTastes\"", "\"moodCategories\"");
    }

    @Test
    @DisplayName("응답 JSON을 IntentExtraction으로 바꾼다 (서버는 요리 이름이 메시지에 있는지만 확인한다)")
    void parsesExtraction() {
        chatModel.reply = EXTRACTION_JSON;

        IntentExtraction result = extractor.extract(1L, 10L, "두부로 할 수 있는 냉면 30분 안에", null);

        assertThat(result).isEqualTo(new IntentExtraction(30, List.of("두부"), null, true, List.of("냉면"), "냉면",
                "냉면 같은 면 요리", null, null, null, null, null));
    }

    @Test
    @DisplayName("응답 JSON이 깨지면 예외를 던진다")
    void throws_whenJsonIsBroken() {
        chatModel.reply = "{\"dishText\": \"냉면\", ";

        assertThatThrownBy(() -> extractor.extract(1L, 10L, "뭐 먹지?", null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("직전 추천이 없으면(첫 질문) 고정 시스템 프롬프트와 사용자 메시지만 보낸다")
    void sendsOnlyFixedSystem_whenNoFollowUp() {
        chatModel.reply = EXTRACTION_JSON;

        extractor.extract(1L, 10L, "뭐 먹지?", null);

        assertThat(chatModel.lastPrompt.getInstructions()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(chatModel.lastPrompt.getContents()).doesNotContain("[이어지는 말에서 꺼내기]");
    }

    @Test
    @DisplayName("직전 추천이 있으면 고정 시스템 프롬프트를 맨 앞에 두고, 직전 추천 정보와 이어지는 말 규칙은 그 뒤 별도 메시지로 보낸다")
    void sendsFollowUpAsSeparateMessage_afterFixedSystem() {
        chatModel.reply = EXTRACTION_JSON;

        extractor.extract(1L, 10L, "두 번째 거 어떻게 만들어?", "[직전 추천] 1. 탕수육 / 2. 깐풍기");

        List<Message> messages = chatModel.lastPrompt.getInstructions();
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.SYSTEM, MessageType.USER);
        assertThat(messages.get(0).getText()).isEqualTo(PROMPT);
        assertThat(messages.get(1).getText()).isEqualTo("[직전 추천] 1. 탕수육 / 2. 깐풍기\n\n[이어지는 말에서 꺼내기] 규칙");
        assertThat(messages.get(2).getText()).startsWith("두 번째 거 어떻게 만들어?");
    }

    @Test
    @DisplayName("응답 스키마는 strict(OpenAI json_schema)로 보내고, 꺼내기 칸만 모두 필수이며 비슷한 요리 칸은 없다 (따로 묻는다)")
    void sendsStrictSchema_withExtractionFieldsOnly() {
        chatModel.reply = EXTRACTION_JSON;

        extractor.extract(1L, 10L, "김피탕", null);

        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.lastPrompt.getOptions();
        // Spring AI 2.0.1은 strict를 따로 정하지 않으면 json_schema를 strict=true로 보낸다 (OpenAiChatModel)
        assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(options.getResponseFormat().getStrict()).isNotEqualTo(Boolean.FALSE);
        JsonNode schema = JsonMapper.builder().build().readTree(options.getOutputSchema());
        List<String> properties = new ArrayList<>(schema.get("properties").propertyNames());
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(value -> required.add(value.asString()));
        assertThat(properties).containsExactlyInAnyOrderElementsOf(FIELDS);
        assertThat(required).containsExactlyInAnyOrderElementsOf(FIELDS);
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
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

package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishes;
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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiSimilarDishGeneratorTest {

    private CapturingChatModel chatModel;
    private OpenAiSimilarDishGenerator generator;

    @BeforeEach
    void setUp() {
        chatModel = new CapturingChatModel();
        generator = new OpenAiSimilarDishGenerator(ChatClient.builder(chatModel).build(), new LlmCostCalculator(0.15, 0.60),
                new ClassPathResource("prompts/chat-similar-dishes.txt"));
    }

    @Test
    @DisplayName("고정 시스템 프롬프트와 요리 이름 하나만 보내고, 조건(맛·시간)은 넘기지 않는다")
    void sendsFixedSystemAndDishOnly() {
        chatModel.reply = "{\"formalName\":\"\",\"dishes\":[\"탕수육\",\"깐풍기\",\"꿔바로우\"]}";

        generator.generate(1L, "김피탕");

        assertThat(chatModel.lastPrompt.getInstructions()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(chatModel.lastPrompt.getSystemMessage().getText()).contains("비슷한 요리 이름 3~5개");
        assertThat(chatModel.lastPrompt.getUserMessage().getText()).startsWith("김피탕");
    }

    @Test
    @DisplayName("넓은 요청 요리는 넓은 요청 프롬프트(prompts/chat-broad-dishes.txt)와 요청 표현 하나만 보낸다")
    void sendsBroadPromptAndRequestOnly() {
        chatModel.reply = "{\"dishes\":[\"오징어볶음\",\"계란찜\"]}";

        SimilarDishes dishes = generator.generate(1L, DishListKind.BROAD, "술안주");

        assertThat(dishes.dishes()).containsExactly("오징어볶음", "계란찜");
        assertThat(dishes.formalName()).isNull();
        assertThat(chatModel.lastPrompt.getSystemMessage().getText()).contains("요청에 맞는 요리 이름 5개")
                .doesNotContain("비슷한 요리 이름 3~5개");
        assertThat(chatModel.lastPrompt.getUserMessage().getText()).startsWith("술안주");
    }

    @Test
    @DisplayName("응답 모양을 strict json_schema(맨 위 객체, 정식 이름 문자열과 dishes 문자열 배열)로 강제하고 정식 이름과 목록을 읽는다")
    void readsDishes_withStrictSchema() {
        chatModel.reply = "{\"formalName\":\"김치피자탕수육\",\"dishes\":[\"탕수육\",\"깐풍기\",\"꿔바로우\"]}";

        SimilarDishes dishes = generator.generate(1L, "김피탕");

        assertThat(dishes.formalName()).isEqualTo("김치피자탕수육");
        assertThat(dishes.dishes()).containsExactly("탕수육", "깐풍기", "꿔바로우");
        assertThat(chatModel.lastPrompt.getSystemMessage().getText())
                .contains("- formalName: 요리 이름이 줄임말이면 정식 이름을 쓴다. 줄임말이 아니거나 모르면 빈 값이다.");
        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.lastPrompt.getOptions();
        assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(options.getResponseFormat().getStrict()).isNotEqualTo(Boolean.FALSE);
        JsonNode schema = JsonMapper.builder().build().readTree(options.getOutputSchema());
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("properties").get("dishes").get("type").asString()).isEqualTo("array");
        assertThat(schema.get("properties").get("dishes").get("items").get("type").asString()).isEqualTo("string");
        assertThat(schema.get("properties").get("formalName").get("type").asString()).isEqualTo("string");
    }

    @Test
    @DisplayName("응답 JSON이 깨지면 예외를 던진다 (부르는 쪽이 비슷한 요리 없이 진행한다)")
    void throws_whenJsonIsBroken() {
        chatModel.reply = "{\"dishes\": [\"탕수육\"";

        assertThatThrownBy(() -> generator.generate(1L, "김피탕")).isInstanceOf(RuntimeException.class);
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

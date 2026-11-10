package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 추천 답 프롬프트 구성 (실제 ChatClient + 가짜 모델)
class OpenAiChatRecommendationReplyTest {

    @Test
    @DisplayName("추천 답은 공통+추천 규칙을 system으로, 후보 정보를 별도 메시지로, 사용자 메시지를 마지막에 보내고 최대 토큰을 120으로 줄인다")
    void sendsRecommendSystemAndCandidateMessage() {
        CapturingStreamModel model = new CapturingStreamModel();
        ChatClient chatClient = ChatClient.builder(model).defaultSystem("기본 {시스템}").build();
        OpenAiChatReplyGenerator generator = new OpenAiChatReplyGenerator(chatClient, new LlmCostCalculator(0.15, 0.60),
                new ChatReplyErrorTranslator(), resource("공통 규칙"), resource("추천 규칙 {예시}"), resource("레시피 규칙"), resource("추천 기준"));

        List<String> chunks = generator.generateRecommendation(1L, 10L, "{두부} 추천해줘", "[추천 후보]\n1. 두부조림")
                .collectList().block();

        assertThat(chunks).containsExactly("추천", "합니다");
        List<Message> messages = model.lastPrompt.getInstructions();
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.SYSTEM, MessageType.USER);
        assertThat(messages.get(0).getText()).isEqualTo("공통 규칙\n\n추천 규칙 {예시}");
        assertThat(messages.get(1).getText()).isEqualTo("[추천 후보]\n1. 두부조림");
        assertThat(messages.get(2).getText()).isEqualTo("{두부} 추천해줘");
        assertThat(model.lastPrompt.getOptions().getMaxTokens()).isEqualTo(OpenAiChatReplyGenerator.RECOMMEND_MAX_TOKENS);
    }

    @Test
    @DisplayName("일반 답은 공통+추천 기준을 system으로 보내고(빈의 기본 system 대신) 최대 토큰을 줄이지 않는다")
    void sendsGeneralSystemWithCriteria_whenGeneral() {
        CapturingStreamModel model = new CapturingStreamModel();
        ChatClient chatClient = ChatClient.builder(model).defaultSystem("기본 시스템").build();
        OpenAiChatReplyGenerator generator = new OpenAiChatReplyGenerator(chatClient, new LlmCostCalculator(0.15, 0.60),
                new ChatReplyErrorTranslator(), resource("공통 규칙"), resource("추천 규칙"), resource("레시피 규칙"), resource("추천 기준"));

        generator.generate(1L, 10L, "두부 보관 어떻게 해?", List.of()).collectList().block();

        assertThat(model.lastPrompt.getInstructions()).extracting(Message::getText)
                .containsExactly("공통 규칙\n\n추천 기준", "두부 보관 어떻게 해?");
        Integer maxTokens = model.lastPrompt.getOptions() == null ? null : model.lastPrompt.getOptions().getMaxTokens();
        assertThat(maxTokens).isNotEqualTo(Integer.valueOf(OpenAiChatReplyGenerator.RECOMMEND_MAX_TOKENS));
    }

    @Test
    @DisplayName("레시피 자세히 답은 공통+레시피 답 규칙을 system으로, 레시피 정보를 별도 메시지로, 사용자 메시지를 마지막에 보내고 최대 토큰을 600으로 둔다")
    void sendsRecipeDetailSystemAndRecipeInfo() {
        CapturingStreamModel model = new CapturingStreamModel();
        ChatClient chatClient = ChatClient.builder(model).defaultSystem("기본 시스템").build();
        OpenAiChatReplyGenerator generator = new OpenAiChatReplyGenerator(chatClient, new LlmCostCalculator(0.15, 0.60),
                new ChatReplyErrorTranslator(), resource("공통 규칙"), resource("추천 규칙"), resource("레시피 규칙"), resource("추천 기준"));

        generator.generateRecipeDetail(1L, 10L, "두 번째 거 어떻게 만들어?", "[레시피 정보]\n이름: 깐풍기").collectList().block();

        List<Message> messages = model.lastPrompt.getInstructions();
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.SYSTEM, MessageType.USER);
        assertThat(messages.get(0).getText()).isEqualTo("공통 규칙\n\n레시피 규칙");
        assertThat(messages.get(1).getText()).isEqualTo("[레시피 정보]\n이름: 깐풍기");
        assertThat(messages.get(2).getText()).isEqualTo("두 번째 거 어떻게 만들어?");
        assertThat(model.lastPrompt.getOptions().getMaxTokens()).isEqualTo(OpenAiChatReplyGenerator.RECIPE_DETAIL_MAX_TOKENS);
        assertThat(OpenAiChatReplyGenerator.RECIPE_DETAIL_MAX_TOKENS).isEqualTo(600);
    }

    @Test
    @DisplayName("일반 답 프롬프트는 음식 말에 설명할 수 없다고 하지 않게 하는 줄, 레시피를 지어내지 않게 하는 줄, 기준 밖의 것을 말하지 않게 하는 줄과 레시피·밀키트 기준 네 줄이다")
    void hasCriteriaLines_inGeneralPrompt() throws Exception {
        String prompt = new ClassPathResource("prompts/chat-general.txt").getContentAsString(StandardCharsets.UTF_8).strip();

        assertThat(prompt.lines()).hasSize(7);
        assertThat(prompt).startsWith("음식·요리·장보기와 관련된 말에").contains("추천해 드릴까요?").contains("[추천 기준]").contains("인기, 평점, 영양처럼 여기 없는 기준은 말하지 않는다")
                .contains("4. 밀키트는").doesNotContain("0.4");
    }

    private static ByteArrayResource resource(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }

    // 마지막 프롬프트를 기억하고 정해 둔 두 조각을 스트리밍하는 가짜 모델
    private static class CapturingStreamModel implements ChatModel {

        private Prompt lastPrompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastPrompt = prompt;
            return Flux.just(chunk("추천"), chunk("합니다"));
        }

        private static ChatResponse chunk(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }
}

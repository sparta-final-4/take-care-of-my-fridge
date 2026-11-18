package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ai.OpenAiSimilarDishGenerator;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.UsageRecorder;
import org.example.takecareofmyfridgebe.global.config.ai.ChatClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * 비슷한 요리 AI 평가 (수동, 실제 OpenAI 호출). 정한 요리 이름마다 비슷한 요리 목록과 개수를 CSV로만 남긴다 (일치 판정 없음).
 * <p>
 * 기본은 꺼져 있다. 의도 판단 평가(ChatIntentEvalManualTest)와 같은 환경 변수를 쓴다 (take-care-of-my-fridge-be 폴더에서):
 * <pre>
 * OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=sk-... ./gradlew cleanTest test --tests '*SimilarDishEvalManualTest' -i
 * (선택) OPENAI_INTENT_EVAL_RUNS=3  요리 이름마다 돌릴 횟수 (기본 3)
 * </pre>
 * 결과: build/reports/chat-intent-eval/similar-{시각}.csv (요리 이름·회차·AI 원래 목록·정리한 목록·개수·토큰·시간). 같은 줄을 콘솔에도 찍는다.
 * 운영과 같게 ChatClientConfig.chatSimilarDishChatClient 옵션과 prompts/chat-similar-dishes.txt를 쓰고, 정리는 SimilarDishFinder.clean과 같다.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_INTENT_EVAL", matches = "true")
class SimilarDishEvalManualTest {

    private static final String MODEL = "gpt-4o-mini";
    private static final Path OUT_DIR = Path.of("build", "reports", "chat-intent-eval");
    // 레시피에 없을 법한 이름(김피탕·나가사키 짬뽕·꿔바로우·마라샹궈)과 오타(김치찌게)
    private static final List<String> DISHES = List.of("김피탕", "나가사키 짬뽕", "꿔바로우", "마라샹궈", "김치찌게");

    @Test
    @DisplayName("정한 요리 이름마다 실제 AI로 비슷한 요리를 묻고 목록과 개수를 CSV로 남긴다")
    void runsSimilarDishes() throws IOException {
        String apiKey = System.getenv("OPENAI_INTENT_EVAL_KEY");
        // 키가 없으면 실패하지 않고 건너뛴다 (평가 플래그만 셸에 남아 있는 경우)
        assumeThat(apiKey).as("OPENAI_INTENT_EVAL_KEY가 없음").isNotBlank();
        int runs = Integer.parseInt(Optional.ofNullable(System.getenv("OPENAI_INTENT_EVAL_RUNS")).orElse("3"));
        UsageRecorder usage = new UsageRecorder();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(apiKey).model(MODEL).build())
                .build();
        ChatClient chatClient = new ChatClientConfig().chatSimilarDishChatClient(ChatClient.builder(model), usage);
        OpenAiSimilarDishGenerator generator = new OpenAiSimilarDishGenerator(chatClient, usage,
                new ClassPathResource("prompts/chat-similar-dishes.txt"));

        List<String> lines = new ArrayList<>();
        lines.add("dish,run,source,aiDishes,dishes,count,inputTokens,outputTokens,ms");
        for (String dish : DISHES) {
            for (int run = 1; run <= runs; run++) {
                usage.clear();
                long startedAt = System.nanoTime();
                String source = "AI";
                List<String> generated = List.of();
                try {
                    generated = generator.generate(1L, dish).dishes();
                } catch (RuntimeException e) {
                    source = "AI_FAILED " + e;
                }
                long ms = (System.nanoTime() - startedAt) / 1_000_000;
                List<String> dishes = SimilarDishFinder.clean(dish, generated);
                lines.add(String.join(",", ChatIntentEvalManualTest.csv(dish), String.valueOf(run),
                        ChatIntentEvalManualTest.csv(source), ChatIntentEvalManualTest.csv(generated),
                        ChatIntentEvalManualTest.csv(dishes), String.valueOf(dishes.size()), String.valueOf(usage.input()),
                        String.valueOf(usage.output()), String.valueOf(ms)));
            }
        }

        Files.createDirectories(OUT_DIR);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = OUT_DIR.resolve("similar-" + stamp + ".csv");
        // 엑셀에서 한글이 깨지지 않게 BOM을 붙인다
        Files.writeString(file, "﻿" + String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        lines.forEach(System.out::println);
        System.out.println("결과: " + file.toAbsolutePath());
    }
}

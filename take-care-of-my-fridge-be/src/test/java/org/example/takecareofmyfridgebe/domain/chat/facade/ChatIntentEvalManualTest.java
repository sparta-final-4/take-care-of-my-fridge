package org.example.takecareofmyfridgebe.domain.chat.facade;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.openai.models.completions.CompletionUsage;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSafetyBlock;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation.QuerySnapshot;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.intent.IntentCorrector;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyExclusionFinder;
import org.example.takecareofmyfridgebe.domain.chat.safety.SafetyWordChecker;
import org.example.takecareofmyfridgebe.domain.chat.ai.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.ai.OpenAiChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.config.ai.ChatClientConfig;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCost;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 상담 의도 판단 평가 (수동, 실제 OpenAI 호출). 프롬프트를 바꾸기 전·후로 같은 평가 세트를 돌려 일치율을 비교한다.
 * <p>
 * 기본은 꺼져 있다. 켜는 법 (take-care-of-my-fridge-be 폴더에서):
 * <pre>
 * OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=sk-... TYPESAFE_API_KEY=... ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
 * (키는 OPENAI_API_KEY가 아니라 OPENAI_INTENT_EVAL_KEY로 넘긴다. build.gradle의 test 작업이 OPENAI_API_KEY를 가짜 키로 덮어쓴다)
 * (필수) TYPESAFE_API_KEY  의도는 늘 Jev로 정한다 (ChatIntentEvalJevOn). 없으면 멈춘다
 * (선택) OPENAI_INTENT_EVAL_RUNS=3  문장마다 돌릴 횟수 (다수결, 기본 3)
 * (선택) OPENAI_INTENT_EVAL_ONLY=F10,F11  이 id만 돌린다
 * (선택) OPENAI_INTENT_EVAL_LABEL=B  결과 파일 이름에 붙일 이름 (eval-{시각}-B-summary.txt)
 * (선택) OPENAI_INTENT_EVAL_SET=holdout  평가 세트 (main: chat-intent-eval.yml, 기본 / holdout: chat-intent-holdout.yml, 과적합 확인용 /
 *                                          safety: chat-intent-safety.yml, 자해 확률 기준값 확인용)
 * </pre>
 * 결과: build/reports/chat-intent-eval/eval-{시각}-runs.csv (한 번 돌린 결과마다 한 줄, AI 원래 값과 최종 값),
 * eval-{시각}-cases.csv (문장마다 다수결 결과), eval-{시각}-summary.txt (묶음별 일치율·토큰·시간). 같은 요약을 콘솔에도 찍는다.
 * <p>
 * 실제 운영과 같게: 단어 차단(SafetyWordChecker)을 먼저 보고, 걸리지 않으면 실제 OpenAiChatIntentExtractor(ChatClientConfig와 같은 옵션) +
 * 실제 ChatIntentFacade(서버 안전망)로 정한다. 단어 설정은 config/*.yml 그대로 읽는다.
 * DB·ES가 필요한 부분(재료 사전, 레시피 이름 찾기, 분류 목록)은 chat-intent-eval.yml의 작은 고정 사전으로 대신하고, 알레르기 찾기는 끈다.
 * 일치율만 보고하고 기대와 달라도 실패시키지 않는다 (기대가 애매한 문장이 있어서). 평가 세트가 비었을 때만 실패한다.
 */
@SpringBootTest(classes = ChatIntentEvalManualTest.class)
@EnableConfigurationProperties({ChatWordProperties.class, ChatCandidateProperties.class, ChatSafetyProperties.class})
@EnabledIfEnvironmentVariable(named = "OPENAI_INTENT_EVAL", matches = "true")
class ChatIntentEvalManualTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final String MODEL = "gpt-4o-mini";
    private static final Path OUT_DIR = Path.of("build", "reports", "chat-intent-eval");
    private static final Encoding TOKENS = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    @Autowired
    private ChatWordProperties words;
    @Autowired
    private ChatCandidateProperties candidateProperties;
    @Autowired
    private ChatSafetyProperties safety;

    @Test
    @DisplayName("평가 세트를 실제 AI로 돌려 서버 안전망을 거친 최종 결과를 기대와 비교하고 CSV로 남긴다")
    void runsEvalSet() throws IOException {
        String setResource = EvalSet.resource(System.getenv("OPENAI_INTENT_EVAL_SET"));
        EvalSet set = EvalSet.load(setResource);
        List<EvalCase> cases = set.cases(onlyIds());
        assertThat(cases).as("평가 세트").isNotEmpty();
        int runs = Integer.parseInt(Optional.ofNullable(System.getenv("OPENAI_INTENT_EVAL_RUNS")).orElse("3"));

        UsageRecorder usage = new UsageRecorder();
        RecordingExtractor extractor = new RecordingExtractor(openAiExtractor(usage));
        ChatIntentEvalJevOn jev = ChatIntentEvalJevOn.create(words);
        List<RunRow> rows = new ArrayList<>();
        try {
            ChatIntentFacade facade = facade(set, extractor, jev);
            for (EvalCase evalCase : cases) {
                for (int run = 1; run <= runs; run++) {
                    rows.add(runOnce(set, facade, extractor, usage, jev, evalCase, run));
                }
            }
        } finally {
            jev.close();
        }
        write(set, setResource, cases, rows, runs);

        // AI 호출이 실패하면 서버가 일반 답으로 정해 결과가 의미 없으므로, 파일을 남긴 뒤 실패시킨다
        List<RunRow> failed = rows.stream().filter(row -> row.source().startsWith("AI_FAILED")).toList();
        assertThat(failed).as("AI 호출 실패 %d번, 첫 원인: %s", failed.size(),
                failed.isEmpty() ? "" : failed.get(0).source()).isEmpty();
    }

    private RunRow runOnce(EvalSet set, ChatIntentFacade facade, RecordingExtractor extractor, UsageRecorder usage,
                           ChatIntentEvalJevOn jev, EvalCase evalCase, int run) {
        Optional<ChatSafetyBlock> block = SafetyWordChecker.check(evalCase.message(), safety.words());
        if (block.isPresent()) {
            Outcome outcome = Outcome.blocked(block.get() == ChatSafetyBlock.SELF_HARM ? ChatIntent.SELF_HARM : ChatIntent.BLOCKED);
            return new RunRow(evalCase, run, outcome, "WORD_" + block.get(), null, 0, 0, 0, 0,
                    evalCase.expect().mismatches(outcome, set), null);
        }
        extractor.last = null;
        extractor.failure = null;
        usage.clear();
        jev.clear();
        long startedAt = System.nanoTime();
        Outcome outcome;
        String error = null;
        try {
            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of(evalCase.message()), set.context(evalCase.context()));
            outcome = Outcome.of(resolution, set);
        } catch (RuntimeException e) {
            outcome = Outcome.blocked(ChatIntent.GENERAL);
            error = e.toString();
        }
        long ms = (System.nanoTime() - startedAt) / 1_000_000;
        ChatIntentEvalJevOn.Info info = jev == null ? null : jev.info();
        List<String> mismatches = info == null ? evalCase.expect().mismatches(outcome, set)
                : ChatIntentEvalJevOn.mismatches(evalCase.expect(), outcome, set, info);
        return new RunRow(evalCase, run, outcome, error != null ? "ERROR " + error
                : extractor.failure != null ? "AI_FAILED " + extractor.failure : "AI", extractor.last,
                usage.input, usage.output, usage.cached, ms, mismatches, info);
    }

    private static Set<String> onlyIds() {
        String only = System.getenv("OPENAI_INTENT_EVAL_ONLY");
        if (only == null || only.isBlank()) {
            return Set.of();
        }
        return Set.of(only.split("\\s*,\\s*"));
    }

    // 운영과 같은 ChatClient 옵션(ChatClientConfig.chatIntentChatClient)과 프롬프트 파일
    private static OpenAiChatIntentExtractor openAiExtractor(UsageRecorder usage) {
        String apiKey = System.getenv("OPENAI_INTENT_EVAL_KEY");
        // 키가 없으면 실패하지 않고 건너뛴다 (평가 플래그만 셸에 남아 있는 경우)
        assumeThat(apiKey).as("OPENAI_INTENT_EVAL_KEY가 없음 (OPENAI_API_KEY는 build.gradle이 가짜 키로 덮어써서 못 씀). "
                + "Git Bash: OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=sk-... ./gradlew ... / "
                + "PowerShell: $env:OPENAI_INTENT_EVAL_KEY='sk-...'; .\\gradlew.bat ...").isNotBlank();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(apiKey).model(MODEL).build())
                .build();
        ChatClient chatClient = new ChatClientConfig().chatIntentChatClient(model, usage, apiKey);
        return new OpenAiChatIntentExtractor(chatClient, usage, new ClassPathResource("prompts/chat-intent.txt"),
                new ClassPathResource("prompts/chat-intent-followup.txt"));
    }

    // 실제 Jev·넓은 요청 요리 이름 AI로 정한다
    private ChatIntentFacade facade(EvalSet set, ChatIntentExtractor extractor, ChatIntentEvalJevOn jev) {
        RecipeService recipeService = mock(RecipeService.class);
        when(recipeService.getClassifications()).thenReturn(new RecipeClassificationResponse(List.of("한식"), set.categories()));
        when(recipeService.suggestNames(anyString(), anyInt())).thenAnswer(invocation -> {
            String keyword = invocation.getArgument(0);
            int limit = invocation.getArgument(1);
            return set.recipes().stream().filter(name -> name.contains(keyword)).limit(limit).toList();
        });
        IngredientMatcher ingredientMatcher = mock(IngredientMatcher.class);
        when(ingredientMatcher.matchAll(anyList())).thenAnswer(invocation -> {
            List<String> names = invocation.getArgument(0);
            Map<String, Ingredient> matched = new LinkedHashMap<>();
            names.forEach(name -> matched.put(name, set.ingredient(name)));
            return matched;
        });
        AllergyExclusionFinder allergyExclusionFinder = mock(AllergyExclusionFinder.class);
        when(allergyExclusionFinder.find(any(), any()))
                .thenReturn(AllergyExclusionFinder.Allergens.NONE);
        IntentCorrector corrector = new IntentCorrector(recipeService, ingredientMatcher, mock(ChatMessageService.class), words);
        return new ChatIntentFacade(extractor, recipeService, ingredientMatcher, candidateProperties, words,
                allergyExclusionFinder, corrector, jev.runner(), jev.resolver());
    }

    private void write(EvalSet set, String setResource, List<EvalCase> cases, List<RunRow> rows, int runs) throws IOException {
        Files.createDirectories(OUT_DIR);
        String label = Optional.ofNullable(System.getenv("OPENAI_INTENT_EVAL_LABEL")).map(String::strip)
                .filter(value -> !value.isEmpty()).map(value -> value.replaceAll("[^A-Za-z0-9_-]", "_")).orElse(null);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + (label == null ? "" : "-" + label);

        List<String> runLines = new ArrayList<>();
        List<String> header = new ArrayList<>(List.of("id", "group", "context", "message", "run", "pass", "mismatch", "source",
                "intent", "wanted", "avoid", "categories", "keywords", "dish", "preference", "maxMinutes", "include", "exclude",
                "useSelectedOnly", "scope", "names", "saleOnly", "productType", "target", "question",
                "aiKeywords", "aiDish", "aiPreference", "aiMaxMinutes",
                "aiInclude", "aiExclude", "aiUseSelectedOnly", "aiScope", "aiTargetIndex", "aiTargetName", "aiRemoved",
                "aiQuestion", "aiSelfHarmRisk", "inputTokens", "cachedTokens", "outputTokens", "ms"));
        header.addAll(ChatIntentEvalJevOn.Info.HEADER);
        runLines.add(String.join(",", header));
        rows.forEach(row -> runLines.add(row.line()));

        List<String> caseLines = new ArrayList<>();
        caseLines.add(String.join(",", "id", "group", "context", "message", "passRuns", "runs", "pass", "mismatches", "expect"));
        Map<String, int[]> byGroup = new LinkedHashMap<>();
        int passedCases = 0;
        List<String> failedLines = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            List<RunRow> caseRows = rows.stream().filter(row -> row.evalCase() == evalCase).toList();
            long passRuns = caseRows.stream().filter(RunRow::pass).count();
            boolean pass = passRuns * 2 > caseRows.size();
            passedCases += pass ? 1 : 0;
            int[] counts = byGroup.computeIfAbsent(evalCase.group(), group -> new int[2]);
            counts[0] += pass ? 1 : 0;
            counts[1]++;
            String mismatches = caseRows.stream().flatMap(row -> row.mismatches().stream()).distinct()
                    .collect(Collectors.joining(" | "));
            caseLines.add(String.join(",", csv(evalCase.id()), csv(evalCase.group()), csv(evalCase.context()),
                    csv(evalCase.message()), String.valueOf(passRuns), String.valueOf(caseRows.size()), String.valueOf(pass),
                    csv(mismatches), csv(evalCase.expect().raw().toString())));
            if (!pass) {
                failedLines.add(String.format("  %s %s (%d/%d) %s", evalCase.id(), evalCase.message(), passRuns, caseRows.size(), mismatches));
            }
        }

        List<RunRow> aiRows = rows.stream().filter(row -> row.inputTokens() > 0).toList();
        List<String> summary = new ArrayList<>();
        // 맨 위: 이번에 끈 서버 보정 (A~E 비교용)
        summary.add("의도: Jev 단독 (OpenAI는 꺼내기만, 서비스 규칙)" + (label == null ? "" : " · 이름 " + label)
                + (EvalSet.MAIN_SET.equals(setResource) ? "" : " · 세트 " + setResource));
        summary.add("AI 상담 의도 평가 " + stamp + " · 모델 " + MODEL + " · 문장 " + cases.size() + "개 × " + runs + "번 (다수결)");
        summary.add(promptSize());
        summary.add(String.format("전체 일치: %d / %d (%.1f%%)", passedCases, cases.size(), 100.0 * passedCases / cases.size()));
        byGroup.forEach((group, counts) -> summary.add(String.format("  %s: %d / %d", group, counts[0], counts[1])));
        long aiFailures = rows.stream().filter(row -> row.source().startsWith("AI_FAILED")).count();
        if (aiFailures > 0) {
            summary.add("AI 호출 실패 " + aiFailures + "번 (실패한 문장은 서버가 일반 답으로 정해 결과가 의미 없음). 첫 원인: "
                    + rows.stream().filter(row -> row.source().startsWith("AI_FAILED")).findFirst().map(RunRow::source).orElse(""));
        }
        summary.add(String.format("AI 호출 %d번 · 평균 입력 %.0f토큰(캐시 %.0f) · 평균 출력 %.0f토큰 · 평균 %.0fms · 최대 %dms",
                aiRows.size(),
                aiRows.stream().mapToLong(RunRow::inputTokens).average().orElse(0),
                aiRows.stream().mapToLong(RunRow::cachedTokens).average().orElse(0),
                aiRows.stream().mapToLong(RunRow::outputTokens).average().orElse(0),
                aiRows.stream().mapToLong(RunRow::ms).average().orElse(0),
                aiRows.stream().mapToLong(RunRow::ms).max().orElse(0)));
        summary.addAll(ChatIntentEvalJevOn.summary(cases, rows, EvalSet.SAFETY_SET.equals(setResource)));
        summary.addAll(selfHarmRiskSummary(cases, rows, EvalSet.SAFETY_SET.equals(setResource)));
        summary.add("틀린 문장:");
        summary.addAll(failedLines);

        Path runsFile = OUT_DIR.resolve("eval-" + stamp + "-runs.csv");
        Path casesFile = OUT_DIR.resolve("eval-" + stamp + "-cases.csv");
        Path summaryFile = OUT_DIR.resolve("eval-" + stamp + "-summary.txt");
        // 엑셀에서 한글이 깨지지 않게 BOM을 붙인다
        Files.writeString(runsFile, "﻿" + String.join("\n", runLines) + "\n", StandardCharsets.UTF_8);
        Files.writeString(casesFile, "﻿" + String.join("\n", caseLines) + "\n", StandardCharsets.UTF_8);
        Files.writeString(summaryFile, String.join("\n", summary) + "\n", StandardCharsets.UTF_8);
        summary.forEach(System.out::println);
        System.out.println("결과: " + runsFile.toAbsolutePath() + ", " + casesFile.getFileName() + ", " + summaryFile.getFileName());
    }

    /**
     * OpenAI 꺼내기의 자해 뜻(selfHarmRisk). 운영에서는 Jev 1차가 실패했을 때만 쓰므로, 평가에서는 Jev와 상관없이 값만 센다.
     * 모든 세트: true가 나온 회차 수. 자해 기준 세트: 문장마다 true 회차 수 (단어 차단에 걸린 문장은 OpenAI를 부르지 않는다).
     */
    static List<String> selfHarmRiskSummary(List<EvalCase> cases, List<RunRow> rows, boolean safetySet) {
        List<RunRow> asked = rows.stream().filter(row -> row.ai() != null).toList();
        long risky = asked.stream().filter(row -> row.ai().hasSelfHarmRisk()).count();
        List<String> lines = new ArrayList<>();
        lines.add(String.format("OpenAI selfHarmRisk true: %d / %d회차", risky, asked.size()));
        if (!safetySet) {
            asked.stream().filter(row -> row.ai().hasSelfHarmRisk())
                    .map(row -> row.evalCase().id()).distinct()
                    .forEach(id -> lines.add("  true가 나온 문장: " + id));
            return lines;
        }
        lines.add("OpenAI selfHarmRisk (문장마다, Jev 실패 때 예비):");
        lines.add("  id | 묶음 | true 회차 / 부른 회차 | 메시지");
        for (EvalCase evalCase : cases) {
            List<RunRow> caseRows = asked.stream().filter(row -> row.evalCase() == evalCase).toList();
            String count = caseRows.isEmpty() ? "단어 차단"
                    : caseRows.stream().filter(row -> row.ai().hasSelfHarmRisk()).count() + " / " + caseRows.size();
            lines.add(String.format("  %s | %s | %s | %s", evalCase.id(), evalCase.group(), count, evalCase.message()));
        }
        return lines;
    }

    // 고정 프롬프트 크기 (첫 질문, 후속 규칙). 토큰은 gpt-4o-mini의 o200k 기준
    private static String promptSize() throws IOException {
        String intent = new ClassPathResource("prompts/chat-intent.txt").getContentAsString(StandardCharsets.UTF_8);
        String followUp = new ClassPathResource("prompts/chat-intent-followup.txt").getContentAsString(StandardCharsets.UTF_8).strip();
        return String.format("프롬프트: 첫 질문 %d자 / %d토큰, 후속 규칙 %d자 / %d토큰",
                intent.codePointCount(0, intent.length()), TOKENS.countTokens(intent),
                followUp.codePointCount(0, followUp.length()), TOKENS.countTokens(followUp));
    }

    static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof Collection<?> values
                ? values.stream().map(String::valueOf).collect(Collectors.joining(" / "))
                : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"").replace("\n", " ") + "\"";
    }

    // 한 번 돌린 결과
    record RunRow(EvalCase evalCase, int run, Outcome outcome, String source, IntentExtraction ai,
                  long inputTokens, long outputTokens, long cachedTokens, long ms, List<String> mismatches,
                  ChatIntentEvalJevOn.Info jev) {

        boolean pass() {
            return mismatches.isEmpty();
        }

        // Jev 칸(ChatIntentEvalJevOn.Info.HEADER)을 뒤에 붙인다 (단어 차단 줄은 빈 칸)
        String line() {
            List<String> cells = new ArrayList<>(List.of(csv(evalCase.id()), csv(evalCase.group()), csv(evalCase.context()),
                    csv(evalCase.message()), String.valueOf(run), String.valueOf(pass()), csv(String.join(" | ", mismatches)),
                    csv(source)));
            cells.addAll(outcome.cells());
            if (ai == null) {
                for (int i = 0; i < 13; i++) {
                    cells.add("");
                }
            } else {
                cells.addAll(List.of(csv(ai.keywords()), csv(ai.dishText()), csv(ai.preferenceText()), csv(ai.maxMinutes()),
                        csv(ai.includeIngredients()), csv(ai.excludeIngredients()), csv(ai.useSelectedOnly()),
                        csv(ai.fridgeScope()), csv(ai.targetIndex()), csv(ai.targetName()), csv(ai.removed()),
                        csv(ai.detailQuestion()), csv(ai.selfHarmRisk())));
            }
            cells.addAll(List.of(String.valueOf(inputTokens), String.valueOf(cachedTokens), String.valueOf(outputTokens),
                    String.valueOf(ms)));
            if (jev == null) {
                ChatIntentEvalJevOn.Info.HEADER.forEach(name -> cells.add(""));
            } else {
                cells.addAll(jev.cells(evalCase.expect().raw()));
            }
            return String.join(",", cells);
        }
    }

    // 의도 칸에서 추천 + 이어짐을 나타내는 끝말 ("RECOMMEND+FOLLOWS", 예전 REFINE과 같은 뜻)
    static final String FOLLOWS_SUFFIX = "+FOLLOWS";
    // 예전 기록(평가 CSV·리플레이 입력)의 다시 추천 의도 이름. 추천 + 이어짐으로 읽는다
    private static final String LEGACY_REFINE = "REFINE";

    // 의도 칸 글: 추천 + 이어짐이면 RECOMMEND+FOLLOWS, 그 밖은 의도 이름 (이어짐은 추천일 때만 붙는다)
    static String intentLabel(ChatIntent intent, boolean follows) {
        return intent == null ? null : intent.name() + (follows ? FOLLOWS_SUFFIX : "");
    }

    // 의도 칸 글의 의도. 예전 기록의 REFINE은 RECOMMEND다
    static ChatIntent intentOf(String label) {
        String name = label.endsWith(FOLLOWS_SUFFIX) ? label.substring(0, label.length() - FOLLOWS_SUFFIX.length()) : label;
        return LEGACY_REFINE.equals(name) ? ChatIntent.RECOMMEND : ChatIntent.valueOf(name);
    }

    // 의도 칸 글의 이어짐. 예전 기록의 REFINE은 이어짐 예다
    static boolean followsOf(String label) {
        return label.endsWith(FOLLOWS_SUFFIX) || LEGACY_REFINE.equals(label);
    }

    /**
     * 의도 일치: 의도와 이어짐이 둘 다 맞는지. 기대 이어짐(follows)을 적지 않았으면 아니오를 기대한다
     * (예전 기대 의도 RECOMMEND는 다시 추천이 아닌 추천이었으므로 예전 점수와 바로 비교된다).
     */
    static boolean intentMatches(Map<String, Object> expect, ChatIntent intent, boolean follows) {
        return Expect.strings(expect.get("intent")).contains(intent.name())
                && follows == Boolean.TRUE.equals(expect.get("follows"));
    }

    // 기대 의도 글 (이어짐 예를 기대하면 +FOLLOWS)
    static String expectedIntentLabel(Map<String, Object> expect) {
        return expect.get("intent") + (Boolean.TRUE.equals(expect.get("follows")) ? FOLLOWS_SUFFIX : "");
    }

    /**
     * 비교할 최종 값. 재료는 ID 대신 평가 세트 사전의 이름으로 바꿔 둔다.
     *
     * @param follows 이어짐 (직전 조건을 이어받은 추천인지)
     */
    record Outcome(ChatIntent intent, boolean follows, Set<String> wanted, Set<String> avoid, Set<String> categories, List<String> keywords,
                   String dish, String preference, Integer maxMinutes, Set<String> include, Set<String> exclude,
                   Boolean useSelectedOnly, String scope, Set<String> names, Boolean saleOnly, String productType,
                   Long target, String question) {

        static Outcome blocked(ChatIntent intent) {
            return new Outcome(intent, false, Set.of(), Set.of(), Set.of(), List.of(), null, null, null, Set.of(), Set.of(),
                    null, null, Set.of(), null, null, null, null);
        }

        static Outcome of(ChatIntentResolution resolution, EvalSet set) {
            RecipeQuery query = resolution.recipeQuery();
            var lookup = resolution.lookup();
            return new Outcome(resolution.intent(), resolution.follows(),
                    query == null ? Set.of() : names(query.wantedTastes()),
                    query == null ? Set.of() : names(query.avoidTastes()),
                    query == null ? Set.of() : Set.copyOf(query.moodCategories()),
                    query == null ? List.of() : query.keywords(),
                    query == null ? null : query.keywordText(),
                    query == null ? null : query.preferenceText(),
                    query == null ? null : query.maxMinutes(),
                    query == null ? Set.of() : set.ingredientNames(query.selectedIngredientIds()),
                    query == null ? Set.of() : set.ingredientNames(query.excludeIngredientIds()),
                    query == null ? null : query.useSelectedOnly(),
                    lookup == null || lookup.scope() == null ? null : lookup.scope().name(),
                    lookup == null ? Set.of() : Set.copyOf(lookup.names()),
                    lookup == null ? null : lookup.saleOnly(),
                    lookup == null ? null : lookup.productType() == null ? "ALL" : lookup.productType().name(),
                    resolution.targetRecipeId(),
                    resolution.detailQuestion() == null ? null : resolution.detailQuestion().name());
        }

        private static Set<String> names(List<Taste> tastes) {
            return tastes.stream().map(Enum::name).collect(Collectors.toSet());
        }

        List<String> cells() {
            return List.of(csv(intentLabel(intent, follows)), csv(wanted), csv(avoid), csv(categories), csv(keywords), csv(dish), csv(preference),
                    csv(maxMinutes), csv(include), csv(exclude), csv(useSelectedOnly), csv(scope), csv(names), csv(saleOnly),
                    csv(productType), csv(target), csv(question));
        }
    }

    record EvalCase(String id, String group, String context, String message, Expect expect) {
    }

    /**
     * 기대 값. 적은 칸만 비교한다 (칸 뜻은 chat-intent-eval.yml 머리말).
     */
    record Expect(Map<String, Object> raw) {

        List<String> mismatches(Outcome actual, EvalSet set) {
            List<String> result = new ArrayList<>();
            raw.forEach((field, expected) -> {
                Optional<String> mismatch = check(field, expected, actual);
                mismatch.ifPresent(result::add);
            });
            return result;
        }

        private Optional<String> check(String field, Object expected, Outcome actual) {
            return switch (field) {
                case "intent" -> intentMatches(raw, actual.intent(), actual.follows())
                        ? Optional.empty() : diff(field, expectedIntentLabel(raw), intentLabel(actual.intent(), actual.follows()));
                // 이어짐은 의도와 함께 본다 (intent)
                case "follows" -> Optional.empty();
                case "wanted" -> sameSet(field, expected, actual.wanted());
                case "avoid" -> sameSet(field, expected, actual.avoid());
                case "categories" -> sameSet(field, expected, actual.categories());
                case "keywordsFirst" -> !actual.keywords().isEmpty() && actual.keywords().get(0).equals(expected)
                        ? Optional.empty() : diff(field, expected, actual.keywords());
                case "keywordsAny" -> actual.keywords().stream().anyMatch(strings(expected)::contains)
                        ? Optional.empty() : diff(field, expected, actual.keywords());
                case "keywordsEmpty" -> actual.keywords().isEmpty() == Boolean.TRUE.equals(expected)
                        ? Optional.empty() : diff(field, expected, actual.keywords());
                case "dish" -> Objects.equals(expected, actual.dish()) ? Optional.empty() : diff(field, expected, actual.dish());
                case "dishEmpty" -> (actual.dish() == null) == Boolean.TRUE.equals(expected)
                        ? Optional.empty() : diff(field, expected, actual.dish());
                case "preference" -> actual.preference() != null && actual.preference().contains(String.valueOf(expected))
                        ? Optional.empty() : diff(field, expected, actual.preference());
                case "preferenceEmpty" -> (actual.preference() == null) == Boolean.TRUE.equals(expected)
                        ? Optional.empty() : diff(field, expected, actual.preference());
                case "maxMinutes" -> ("none".equals(expected) ? actual.maxMinutes() == null
                        : Objects.equals(((Number) expected).intValue(), actual.maxMinutes()))
                        ? Optional.empty() : diff(field, expected, actual.maxMinutes());
                case "include" -> sameSet(field, expected, actual.include());
                case "exclude" -> sameSet(field, expected, actual.exclude());
                case "useSelectedOnly" -> Objects.equals(expected, actual.useSelectedOnly())
                        ? Optional.empty() : diff(field, expected, actual.useSelectedOnly());
                case "scope" -> Objects.equals(expected, actual.scope()) ? Optional.empty() : diff(field, expected, actual.scope());
                case "names" -> sameSet(field, expected, actual.names());
                case "saleOnly" -> Objects.equals(expected, actual.saleOnly())
                        ? Optional.empty() : diff(field, expected, actual.saleOnly());
                case "productType" -> Objects.equals(expected, actual.productType())
                        ? Optional.empty() : diff(field, expected, actual.productType());
                case "target" -> Objects.equals(((Number) expected).longValue(), actual.target())
                        ? Optional.empty() : diff(field, expected, actual.target());
                // 요청 종류는 Jev 평가(JevIntentEvalManualTest)만 본다. OpenAI 의도 판단에는 이 칸이 없다
                case "kind" -> Optional.empty();
                case "question" -> Objects.equals(expected, actual.question())
                        ? Optional.empty() : diff(field, expected, actual.question());
                default -> throw new IllegalArgumentException("모르는 기대 칸: " + field);
            };
        }

        private static Optional<String> sameSet(String field, Object expected, Set<String> actual) {
            return Set.copyOf(strings(expected)).equals(actual) ? Optional.empty() : diff(field, expected, actual);
        }

        private static Optional<String> diff(String field, Object expected, Object actual) {
            return Optional.of(field + ": 기대 " + expected + ", 실제 " + actual);
        }

        static List<String> strings(Object value) {
            if (value instanceof Collection<?> values) {
                return values.stream().map(String::valueOf).toList();
            }
            return List.of(String.valueOf(value));
        }
    }

    /**
     * chat-intent-eval.yml(기본) 또는 chat-intent-holdout.yml(확인용). 재료 사전·레시피 이름·분류·맥락·문장.
     */
    record EvalSet(Map<String, Long> ingredients, List<String> recipes, List<String> categories,
                   Map<String, ChatContext> contexts, List<EvalCase> allCases, Set<String> promptExcluded) {

        static final String MAIN_SET = "chat-intent-eval.yml";
        static final String HOLDOUT_SET = "chat-intent-holdout.yml";
        static final String SAFETY_SET = "chat-intent-safety.yml";

        // OPENAI_INTENT_EVAL_SET 값(main|holdout|safety, 없으면 main)을 읽을 평가 세트 파일로 바꾼다
        static String resource(String set) {
            String name = set == null ? "" : set.strip();
            return switch (name) {
                case "", "main" -> MAIN_SET;
                case "holdout" -> HOLDOUT_SET;
                case "safety" -> SAFETY_SET;
                default -> throw new IllegalArgumentException("모르는 평가 세트: " + set + " (main|holdout|safety)");
            };
        }

        static EvalSet load() throws IOException {
            return load(MAIN_SET);
        }

        @SuppressWarnings("unchecked")
        static EvalSet load(String resource) throws IOException {
            Map<String, Object> root;
            try (InputStream in = new ClassPathResource(resource).getInputStream()) {
                root = new Yaml().load(in);
            }
            Map<String, Long> ingredients = new LinkedHashMap<>();
            ((Map<String, Object>) root.get("ingredients")).forEach((name, id) -> ingredients.put(name, ((Number) id).longValue()));
            Map<String, ChatContext> contexts = new LinkedHashMap<>();
            ((Map<String, Map<String, Object>>) root.get("contexts")).forEach((name, value) -> contexts.put(name, context(value)));
            List<EvalCase> cases = ((List<Map<String, Object>>) root.get("cases")).stream()
                    .map(item -> new EvalCase(String.valueOf(item.get("id")), String.valueOf(item.get("group")),
                            (String) item.get("context"), String.valueOf(item.get("message")),
                            new Expect((Map<String, Object>) item.get("expect"))))
                    .toList();
            Set<String> ids = new HashSet<>();
            cases.forEach(evalCase -> assertThat(ids.add(evalCase.id())).as("평가 세트 id 중복: " + evalCase.id()).isTrue());
            EvalSet set = new EvalSet(ingredients, (List<String>) root.get("recipes"), (List<String>) root.get("categories"),
                    contexts, cases, Set.of("양념", "음료", "기타"));
            // 기대 칸 이름이 틀렸으면 AI를 부르기 전에 멈춘다
            cases.forEach(evalCase -> evalCase.expect().mismatches(Outcome.blocked(ChatIntent.GENERAL), set));
            return set;
        }

        @SuppressWarnings("unchecked")
        private static ChatContext context(Map<String, Object> value) {
            List<CardName> cards = ((List<List<Object>>) value.get("cards")).stream()
                    .map(card -> new CardName(((Number) card.get(0)).longValue(), String.valueOf(card.get(1))))
                    .toList();
            Map<String, Object> condition = (Map<String, Object>) value.getOrDefault("condition", Map.of());
            List<Taste> wanted = ((List<String>) condition.getOrDefault("wanted", List.of())).stream().map(Taste::valueOf).toList();
            Integer maxMinutes = condition.get("maxMinutes") == null ? null : ((Number) condition.get("maxMinutes")).intValue();
            QuerySnapshot snapshot = new QuerySnapshot(List.of(), wanted,
                    (List<String>) condition.getOrDefault("categories", List.of()), maxMinutes, List.of(), false,
                    (List<String>) condition.getOrDefault("keywords", List.of()), (String) condition.get("dish"), null, List.of());
            LastRecommendation last = new LastRecommendation(snapshot, cards.stream().map(CardName::recipeId).toList());
            return new ChatContext(List.of(), last, cards);
        }

        List<EvalCase> cases(Set<String> only) {
            return only.isEmpty() ? allCases : allCases.stream().filter(evalCase -> only.contains(evalCase.id())).toList();
        }

        ChatContext context(String name) {
            if (name == null) {
                return ChatContext.empty();
            }
            return Objects.requireNonNull(contexts.get(name), "모르는 맥락: " + name);
        }

        // AI에 보내는 분류 목록 (ChatIntentFacade.categories()와 같게 후보 제외 분류·기타를 뺀 것)
        List<String> promptCategories() {
            return categories.stream().filter(category -> !promptExcluded.contains(category)).toList();
        }

        // 사전에 있으면 그 재료, 없으면 직접 입력 재료 (IngredientMatcher처럼 이름마다 하나씩 돌려준다)
        Ingredient ingredient(String name) {
            Long id = ingredients.get(name);
            Ingredient ingredient = Ingredient.builder()
                    .ingredientCode(id == null ? Ingredient.ING_CUSTOM : "ING_EVAL_" + id)
                    .name(name)
                    .baseUnit("g")
                    .build();
            ReflectionTestUtils.setField(ingredient, "id", id == null ? -1L : id);
            return ingredient;
        }

        Set<String> ingredientNames(List<Long> ids) {
            Map<Long, String> byId = ingredients.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            return ids.stream().map(id -> byId.getOrDefault(id, "id:" + id)).collect(Collectors.toSet());
        }
    }

    // 실제 OpenAI 꺼내기를 부르고 AI가 준 원래 값(정리 전)을 남긴다
    static final class RecordingExtractor implements ChatIntentExtractor {

        private final ChatIntentExtractor delegate;
        private IntentExtraction last;
        private String failure;

        RecordingExtractor(ChatIntentExtractor delegate) {
            this.delegate = delegate;
        }

        @Override
        public IntentExtraction extract(Long memberId, Long sessionId, String message, String followUp) {
            try {
                last = delegate.extract(memberId, sessionId, message, followUp);
                return last;
            } catch (RuntimeException e) {
                failure = e.toString();
                throw e;
            }
        }
    }

    // 운영과 같은 단가로 계산하면서, 마지막 호출의 입력·출력·캐시 토큰을 남긴다
    static final class UsageRecorder extends LlmCostCalculator {

        private long input;
        private long output;
        private long cached;

        UsageRecorder() {
            super(0.15, 0.60);
        }

        void clear() {
            input = 0;
            output = 0;
            cached = 0;
        }

        // 마지막 호출의 입력·출력 토큰 (비슷한 요리 평가 SimilarDishEvalManualTest도 쓴다)
        long input() {
            return input;
        }

        long output() {
            return output;
        }

        @Override
        public Optional<LlmCost> calculate(ChatResponse chatResponse) {
            Optional<LlmCost> cost = super.calculate(chatResponse);
            cost.ifPresent(value -> {
                input = value.inputTokens();
                output = value.outputTokens();
                cached = cachedTokens(chatResponse);
            });
            return cost;
        }

        private static long cachedTokens(ChatResponse chatResponse) {
            Object nativeUsage = chatResponse.getMetadata().getUsage().getNativeUsage();
            if (nativeUsage instanceof CompletionUsage usage) {
                return usage.promptTokensDetails().flatMap(details -> details.cachedTokens()).orElse(0L);
            }
            return 0;
        }
    }
}

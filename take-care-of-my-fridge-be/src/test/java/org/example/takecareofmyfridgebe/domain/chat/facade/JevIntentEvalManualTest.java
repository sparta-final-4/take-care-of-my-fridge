package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentClassifier;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSafetyBlock;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalCase;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalSet;
import org.example.takecareofmyfridgebe.domain.chat.safety.SafetyWordChecker;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.global.config.TypeSafeConfig;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jev(TypeSafe 분류 모델) 상담 의도 판단 평가 (수동, 실제 TypeSafe 호출). ChatIntentEvalManualTest와 같은 평가 세트(chat-intent-eval.yml)로
 * Jev가 의도·요청 종류·분류·맛을 맞히는지, 믿을 confidence 기준값을 숫자로 본다.
 * <p>
 * 기본은 꺼져 있다. 켜는 법 (take-care-of-my-fridge-be 폴더에서, Git Bash):
 * <pre>
 * JEV_INTENT_EVAL=true TYPESAFE_API_KEY=... ./gradlew cleanTest test --tests '*JevIntentEvalManualTest' -i
 * (선택) JEV_INTENT_EVAL_RUNS=3            문장마다 돌릴 횟수 (다수결, 기본 3)
 * (선택) JEV_INTENT_EVAL_ONLY=F10,F11      이 id만 돌린다
 * (선택) JEV_INTENT_EVAL_LABEL=B           결과 파일 이름에 붙일 이름
 * (선택) OPENAI_EVAL_RUNS_CSV=build/reports/chat-intent-eval/eval-{시각}-runs.csv
 *        OpenAI 평가 결과와 같은 id끼리 나란히 놓은 jev-{시각}-compare.csv와 요약을 만든다
 * </pre>
 * 결과: build/reports/chat-intent-eval/jev-{시각}-runs.csv (회차마다 한 줄), jev-{시각}-cases.csv (문장마다 다수결),
 * jev-{시각}-summary.txt (같은 요약을 콘솔에도 찍는다).
 * <p>
 * 운영 그림자 모드와 같게: 단어 차단(SafetyWordChecker)에 걸리면 Jev를 부르지 않는다(기존 평가처럼 BLOCKED·SELF_HARM으로 본다).
 * 걸리지 않으면 실제 JevIntentClassifier를 부른다. 분류 목록은 OpenAI 의도 판단과 같은 목록(평가 세트 분류에서 후보 제외·기타를 뺀 것),
 * 직전 추천 문맥이 있는 문장은 운영과 같은 직전 추천 글(ChatIntentFacade.followUp)을 넘긴다.
 * <p>
 * 비교 칸 (Jev가 판단하지 않는 요리 이름·키워드·시간·재료 등은 비교하지 않는다):
 * <ul>
 *   <li>intent: JevIntentClassifier가 서비스 규칙을 적용한 의도(직전 추천이 없는데 REFINE·RECIPE_DETAIL이면 RECOMMEND, 이때 2차도 묻는다).
 *       그 밖의 서버 보정은 하지 않는다</li>
 *   <li>kind: 요청 종류 (기대 칸 kind)</li>
 *   <li>category: 기대 kind가 특정 분류(CATEGORY)이고 기대 분류가 하나인 문장만, Jev 1등 분류와 비교한다.
 *       넓은 요청 문장의 분류 기대값은 비교하지 않는다</li>
 *   <li>wanted·avoid: 원함·피함 답. 매운맛을 피한다고 답하면서 순한맛을 원한다고 하거나, 매운맛을 원한다고 답하면서 순한맛을 피한다고 한 것은
 *       틀림으로 세지 않는다(그 칸의 기대에 순한맛이 없을 때 그 순한맛을 빼고 비교). 나머지 맛은 그대로 비교한다</li>
 * </ul>
 * 일치율로 실패시키지 않는다. 평가 세트가 비었거나 Jev 호출이 실패했을 때만(결과 파일을 남긴 뒤) 실패한다.
 */
@SpringBootTest(classes = JevIntentEvalManualTest.class)
@EnableConfigurationProperties(ChatSafetyProperties.class)
@EnabledIfEnvironmentVariable(named = "JEV_INTENT_EVAL", matches = "true")
class JevIntentEvalManualTest {

    private static final Path OUT_DIR = Path.of("build", "reports", "chat-intent-eval");
    // 비교 칸. 밀키트는 평가 세트에 기대 칸이 없어 비교하지 않는다
    static final List<String> COMPARED = List.of("intent", "kind", "category", "wanted", "avoid");
    // OpenAI 결과와 함께 비교할 수 있는 칸 (OpenAI는 요청 종류·1등 분류를 내지 않는다)
    private static final List<String> SHARED = List.of("intent", "wanted", "avoid");
    private static final double[] CONFIDENCE_EDGES = {0.95, 0.9, 0.8, 0.7};
    // 입력 100만 토큰당 비용 (USD)
    private static final double USD_PER_MILLION_INPUT = 0.04;
    // 2차(추천 조건)를 묻는 의도 (JevIntentClassifier와 같다)
    private static final Set<ChatIntent> SECOND_INTENTS = Set.of(ChatIntent.RECOMMEND, ChatIntent.REFINE);
    private static final String SPICY = Taste.SPICY.name();
    private static final String MILD = Taste.MILD.name();

    @Autowired
    private ChatSafetyProperties safety;

    @Test
    @DisplayName("평가 세트를 실제 Jev로 돌려 의도·요청 종류·분류·맛을 기대와 비교하고 confidence별 결과를 CSV로 남긴다")
    void runsEvalSet() throws IOException {
        EvalSet set = EvalSet.load();
        List<EvalCase> cases = set.cases(onlyIds());
        assertThat(cases).as("평가 세트").isNotEmpty();
        int runs = Integer.parseInt(Optional.ofNullable(System.getenv("JEV_INTENT_EVAL_RUNS")).orElse("3"));
        String apiKey = System.getenv("TYPESAFE_API_KEY");
        assertThat(apiKey).as("TYPESAFE_API_KEY가 없음. Git Bash: JEV_INTENT_EVAL=true TYPESAFE_API_KEY=... ./gradlew ...").isNotBlank();

        TypeSafeProperties properties = new TypeSafeProperties(null, apiKey, null, null);
        JevIntentClassifier classifier = new JevIntentClassifier(
                new TypeSafeClient(new TypeSafeConfig().typeSafeRestClient(properties), properties));
        List<String> categories = set.promptCategories();

        List<JevRun> rows = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            for (int run = 1; run <= runs; run++) {
                rows.add(runOnce(set, classifier, categories, evalCase, run));
            }
        }
        write(cases, rows, runs, properties.model(), categories.size());

        // Jev 호출이 실패한 회차는 결과가 의미 없으므로, 파일을 남긴 뒤 실패시킨다
        List<JevRun> failed = rows.stream().filter(row -> row.status().endsWith("FAILED")).toList();
        assertThat(failed).as("Jev 호출 실패 %d번 (%s)", failed.size(),
                failed.stream().map(row -> row.evalCase().id() + " " + row.status()).collect(Collectors.joining(", "))).isEmpty();
    }

    private JevRun runOnce(EvalSet set, JevIntentClassifier classifier, List<String> categories, EvalCase evalCase, int run) {
        Optional<ChatSafetyBlock> block = SafetyWordChecker.check(evalCase.message(), safety.words());
        if (block.isPresent()) {
            ChatIntent intent = block.get() == ChatSafetyBlock.SELF_HARM ? ChatIntent.SELF_HARM : ChatIntent.BLOCKED;
            return new JevRun(evalCase, run, "WORD_" + block.get(), intent, null, 0);
        }
        String followUp = ChatIntentFacade.followUp(set.context(evalCase.context()));
        long startedAt = System.nanoTime();
        Optional<JevIntentJudgement> judgement = classifier.classify(evalCase.message(), categories, followUp);
        long ms = (System.nanoTime() - startedAt) / 1_000_000;
        String status = judgement.map(found -> SECOND_INTENTS.contains(found.first().intent()) && !found.hasSecond()
                ? "SECOND_FAILED" : "OK").orElse("FIRST_FAILED");
        return new JevRun(evalCase, run, status, null, judgement.orElse(null), ms);
    }

    private static Set<String> onlyIds() {
        String only = System.getenv("JEV_INTENT_EVAL_ONLY");
        if (only == null || only.isBlank()) {
            return Set.of();
        }
        return Set.of(only.split("\\s*,\\s*"));
    }

    /**
     * 비교에 쓰는 답 하나 (Jev 또는 OpenAI).
     *
     * @param kind     요청 종류. 2차를 묻지 않았거나 OpenAI면 null
     * @param category 1등 분류 ("없음"이면 null)
     */
    record Answer(ChatIntent intent, Set<String> wanted, Set<String> avoid, RequestKind kind, String category) {
    }

    /**
     * 기대와 다른 칸 ("칸: 기대 …, 실제 …"). fields에 든 칸만 본다.
     */
    static List<String> mismatches(Map<String, Object> expect, Answer answer, List<String> fields) {
        List<String> result = new ArrayList<>();
        if (fields.contains("intent") && expect.containsKey("intent")
                && !strings(expect.get("intent")).contains(answer.intent().name())) {
            result.add("intent: 기대 " + expect.get("intent") + ", 실제 " + answer.intent());
        }
        if (fields.contains("kind") && expect.containsKey("kind")
                && (answer.kind() == null || !answer.kind().name().equals(String.valueOf(expect.get("kind"))))) {
            result.add("kind: 기대 " + expect.get("kind") + ", 실제 " + answer.kind());
        }
        if (fields.contains("category") && expectsCategory(expect)
                && !strings(expect.get("categories")).get(0).equals(answer.category())) {
            result.add("category: 기대 " + strings(expect.get("categories")).get(0) + ", 실제 " + answer.category());
        }
        if (fields.contains("wanted") && expect.containsKey("wanted")) {
            Set<String> expected = Set.copyOf(strings(expect.get("wanted")));
            Set<String> actual = withoutOppositeMild(answer.wanted(), answer.avoid(), expected);
            if (!expected.equals(actual)) {
                result.add("wanted: 기대 " + expect.get("wanted") + ", 실제 " + new TreeSet<>(answer.wanted()));
            }
        }
        if (fields.contains("avoid") && expect.containsKey("avoid")) {
            Set<String> expected = Set.copyOf(strings(expect.get("avoid")));
            Set<String> actual = withoutOppositeMild(answer.avoid(), answer.wanted(), expected);
            if (!expected.equals(actual)) {
                result.add("avoid: 기대 " + expect.get("avoid") + ", 실제 " + new TreeSet<>(answer.avoid()));
            }
        }
        return result;
    }

    // 다른 쪽에 매운맛이 있고 이 칸의 기대에 순한맛이 없으면, 이 칸의 순한맛은 매운맛의 반대로 붙은 것으로 보고 뺀다
    static Set<String> withoutOppositeMild(Set<String> tastes, Set<String> otherSide, Set<String> expected) {
        if (!tastes.contains(MILD) || !otherSide.contains(SPICY) || expected.contains(MILD)) {
            return tastes;
        }
        return tastes.stream().filter(taste -> !taste.equals(MILD)).collect(Collectors.toSet());
    }

    // 분류를 비교하는 문장: 기대 kind가 특정 분류이고 기대 분류가 하나
    static boolean expectsCategory(Map<String, Object> expect) {
        return RequestKind.CATEGORY.name().equals(String.valueOf(expect.get("kind")))
                && expect.containsKey("categories") && strings(expect.get("categories")).size() == 1;
    }

    // 그 칸을 이 문장에서 비교하는지
    static boolean compares(Map<String, Object> expect, String field) {
        return "category".equals(field) ? expectsCategory(expect) : expect.containsKey(field);
    }

    /**
     * 한 번 돌린 결과.
     *
     * @param status        OK / FIRST_FAILED / SECOND_FAILED / WORD_{차단 종류}
     * @param blockedIntent 단어 차단에 걸렸을 때의 의도 (Jev를 부르지 않음)
     * @param judgement     Jev 결과 (단어 차단·1차 실패면 null)
     * @param ms            classify 전체 시간
     */
    record JevRun(EvalCase evalCase, int run, String status, ChatIntent blockedIntent,
                  JevIntentJudgement judgement, long ms) {

        boolean judged() {
            return judgement != null;
        }

        Map<String, Object> expect() {
            return evalCase.expect().raw();
        }

        // 비교할 답. 1차 실패면 null
        Answer answer() {
            if (blockedIntent != null) {
                return new Answer(blockedIntent, Set.of(), Set.of(), null, null);
            }
            if (judgement == null) {
                return null;
            }
            JevIntentJudgement.Second second = judgement.second();
            return new Answer(judgement.first().intent(),
                    second == null ? Set.of() : tastes(second, TasteStance.WANTED),
                    second == null ? Set.of() : tastes(second, TasteStance.AVOIDED),
                    second == null ? null : second.requestKind(),
                    second == null ? null : second.category());
        }

        List<String> mismatches(List<String> fields) {
            Answer answer = answer();
            if (answer == null) {
                return List.of("status: " + status);
            }
            return JevIntentEvalManualTest.mismatches(expect(), answer, fields);
        }

        List<String> mismatches() {
            return mismatches(COMPARED);
        }

        // 결과를 비교할 때 쓰는 한 줄 (의도·맛·요청 종류·1등 분류)
        String signature() {
            Answer answer = answer();
            return answer == null ? status
                    : answer.intent() + " " + new TreeSet<>(answer.wanted()) + " " + new TreeSet<>(answer.avoid())
                    + " " + answer.kind() + " " + answer.category();
        }

        private static Set<String> tastes(JevIntentJudgement.Second second, TasteStance stance) {
            return second.tastes().entrySet().stream()
                    .filter(entry -> entry.getValue().stance() == stance)
                    .map(entry -> entry.getKey().name())
                    .collect(Collectors.toSet());
        }
    }

    private void write(List<EvalCase> cases, List<JevRun> rows, int runs, String model, int categoryCount) throws IOException {
        Files.createDirectories(OUT_DIR);
        String label = Optional.ofNullable(System.getenv("JEV_INTENT_EVAL_LABEL")).map(String::strip)
                .filter(value -> !value.isEmpty()).map(value -> value.replaceAll("[^A-Za-z0-9_-]", "_")).orElse(null);
        String stamp = "jev-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + (label == null ? "" : "-" + label);
        Map<EvalCase, List<JevRun>> byCase = new LinkedHashMap<>();
        cases.forEach(evalCase -> byCase.put(evalCase, rows.stream().filter(row -> row.evalCase() == evalCase).toList()));

        List<String> summary = new ArrayList<>();
        summary.add("Jev 상담 의도 평가 " + stamp + " · 모델 " + model + " · 문장 " + cases.size() + "개 × " + runs
                + "번 (다수결) · 분류 선택지 " + categoryCount + "개 + 없음");
        summary.add("비교 칸: " + String.join(", ", COMPARED) + " (밀키트는 평가 세트에 기대 칸이 없어 비교하지 않음)");
        summary.add("- intent: JevIntentClassifier의 서비스 규칙 적용 값 (직전 추천이 없는데 REFINE·RECIPE_DETAIL이면 RECOMMEND, 이때 2차도 묻는다)");
        summary.add("- kind: 요청 종류(DISH 특정 요리 / CATEGORY 특정 분류 / BROAD 넓은 요청 / NONE 없음)");
        summary.add("- category: 기대 kind가 CATEGORY이고 기대 분류가 하나인 문장만 Jev 1등 분류와 비교 (넓은 요청의 분류 기대값은 비교하지 않음)");
        summary.add("- wanted·avoid (기준 변경): 매운맛 피함 + 순한맛 원함, 매운맛 원함 + 순한맛 피함으로 답한 순한맛은 틀림으로 세지 않는다"
                + " (\"순한맛은 직접 말해야만 원함\" 약속을 버림). 나머지 맛은 그대로 비교");
        summary.addAll(accuracy(byCase));
        summary.addAll(kindMatrix(byCase));
        summary.addAll(confidenceBuckets(rows));
        summary.addAll(kindConfidence(rows));
        summary.addAll(determinism(byCase));
        summary.addAll(latencyAndCost(rows));
        summary.addAll(failedCases(byCase));

        Path compareFile = null;
        String openAiRuns = System.getenv("OPENAI_EVAL_RUNS_CSV");
        if (openAiRuns != null && !openAiRuns.isBlank()) {
            compareFile = OUT_DIR.resolve(stamp + "-compare.csv");
            summary.addAll(OpenAiComparison.write(Path.of(openAiRuns.strip()), byCase, compareFile));
        }

        Path runsFile = OUT_DIR.resolve(stamp + "-runs.csv");
        Path casesFile = OUT_DIR.resolve(stamp + "-cases.csv");
        Path summaryFile = OUT_DIR.resolve(stamp + "-summary.txt");
        // 엑셀에서 한글이 깨지지 않게 BOM을 붙인다
        Files.writeString(runsFile, "﻿" + String.join("\n", runLines(rows)) + "\n", StandardCharsets.UTF_8);
        Files.writeString(casesFile, "﻿" + String.join("\n", caseLines(byCase)) + "\n", StandardCharsets.UTF_8);
        Files.writeString(summaryFile, String.join("\n", summary) + "\n", StandardCharsets.UTF_8);
        summary.forEach(System.out::println);
        System.out.println("결과: " + runsFile.toAbsolutePath() + ", " + casesFile.getFileName() + ", " + summaryFile.getFileName()
                + (compareFile == null ? "" : ", " + compareFile.getFileName()));
    }

    // a. 전체·묶음별(다수결)·칸별 일치율
    private static List<String> accuracy(Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        Map<String, int[]> byGroup = new LinkedHashMap<>();
        int passed = 0;
        for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
            boolean pass = majority(entry.getValue(), row -> row.mismatches().isEmpty());
            passed += pass ? 1 : 0;
            int[] counts = byGroup.computeIfAbsent(entry.getKey().group(), group -> new int[2]);
            counts[0] += pass ? 1 : 0;
            counts[1]++;
        }
        lines.add("");
        lines.add("[a] 일치율 (문장 다수결)");
        lines.add(String.format("전체 일치: %d / %d (%.1f%%)", passed, byCase.size(), percent(passed, byCase.size())));
        byGroup.forEach((group, counts) -> lines.add(String.format("  %s: %d / %d", group, counts[0], counts[1])));
        lines.add("칸별 (그 칸을 비교하는 문장만):");
        for (String field : COMPARED) {
            int total = 0;
            int ok = 0;
            for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
                if (!compares(entry.getKey().expect().raw(), field)) {
                    continue;
                }
                total++;
                ok += majority(entry.getValue(), row -> fieldPasses(row.mismatches(), field)) ? 1 : 0;
            }
            lines.add(String.format("  %s: %d / %d (%.1f%%)", field, ok, total, percent(ok, total)));
        }
        return lines;
    }

    // b. 요청 종류: 기대(행) × Jev 다수결 답(열). 2차가 없으면(추천이 아니라고 봄) "2차 없음"
    private static List<String> kindMatrix(Map<EvalCase, List<JevRun>> byCase) {
        List<String> columns = new ArrayList<>();
        for (RequestKind kind : RequestKind.values()) {
            columns.add(kind.name());
        }
        columns.add("2차 없음");
        Map<RequestKind, Map<String, Integer>> matrix = new EnumMap<>(RequestKind.class);
        for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
            Object expected = entry.getKey().expect().raw().get("kind");
            if (expected == null) {
                continue;
            }
            String actual = mostCommon(entry.getValue().stream().filter(row -> row.answer() != null).toList(),
                    row -> row.answer().kind() == null ? "2차 없음" : row.answer().kind().name());
            matrix.computeIfAbsent(RequestKind.valueOf(String.valueOf(expected)), kind -> new LinkedHashMap<>())
                    .merge(actual, 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("[b] 요청 종류 (행: 기대, 열: Jev 다수결 답, 문장 수)");
        lines.add("  기대 \\ Jev  " + String.join("  ", columns));
        matrix.forEach((expected, counts) -> lines.add("  " + expected + "  " + columns.stream()
                .map(column -> column + "=" + counts.getOrDefault(column, 0)).collect(Collectors.joining("  "))));
        return lines;
    }

    // c. intent confidence 구간별 회차 수와 의도 정답률 (Jev를 부른 회차, 의도를 기대한 문장만)
    private static List<String> confidenceBuckets(List<JevRun> rows) {
        List<JevRun> targets = rows.stream().filter(row -> row.judged() && row.expect().containsKey("intent")).toList();
        return buckets("[c] intent confidence 구간별 의도 정답률 (회차 기준, 단어 차단·1차 실패 제외)", targets,
                row -> row.judgement().first().intentConfidence(), row -> fieldPasses(row.mismatches(), "intent"));
    }

    // d. 요청 종류 confidence 구간별 정답률 (2차가 있고 kind를 기대한 회차)
    private static List<String> kindConfidence(List<JevRun> rows) {
        List<JevRun> targets = rows.stream()
                .filter(row -> row.judged() && row.judgement().hasSecond() && row.expect().containsKey("kind")).toList();
        List<String> lines = buckets("[d] 요청 종류 confidence 구간별 정답률 (회차 기준, 2차가 있는 회차)", targets,
                row -> row.judgement().second().requestKindConfidence(), row -> fieldPasses(row.mismatches(), "kind"));
        List<JevRun> categoryTargets = rows.stream()
                .filter(row -> row.judged() && row.judgement().hasSecond() && expectsCategory(row.expect())).toList();
        lines.addAll(buckets("    분류 confidence 구간별 1등 분류 정답률 (기대 kind가 CATEGORY인 회차)", categoryTargets,
                row -> row.judgement().second().categoryConfidence(), row -> fieldPasses(row.mismatches(), "category")));
        return lines;
    }

    private static List<String> buckets(String title, List<JevRun> targets, Function<JevRun, Double> confidence,
                                        Predicate<JevRun> passes) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(title);
        String[] names = {"≥0.95", "0.9~0.95", "0.8~0.9", "0.7~0.8", "<0.7"};
        int[] total = new int[names.length];
        int[] ok = new int[names.length];
        for (JevRun row : targets) {
            int bucket = bucket(confidence.apply(row));
            total[bucket]++;
            ok[bucket] += passes.test(row) ? 1 : 0;
        }
        for (int i = 0; i < names.length; i++) {
            lines.add(String.format("  %-9s 회차 %3d · 정답 %3d (%.1f%%)", names[i], total[i], ok[i], percent(ok[i], total[i])));
        }
        // 기준값을 정할 때: 이 값 이상만 믿으면 몇 %를 쓰고 그중 몇 %가 맞는가
        int keptTotal = 0;
        int keptOk = 0;
        for (int i = 0; i < CONFIDENCE_EDGES.length; i++) {
            keptTotal += total[i];
            keptOk += ok[i];
            lines.add(String.format("  confidence ≥ %.2f만 믿으면: 회차의 %.1f%% 사용, 그중 정답 %.1f%%", CONFIDENCE_EDGES[i],
                    percent(keptTotal, targets.size()), percent(keptOk, keptTotal)));
        }
        return lines;
    }

    private static int bucket(double confidence) {
        for (int i = 0; i < CONFIDENCE_EDGES.length; i++) {
            if (confidence >= CONFIDENCE_EDGES[i]) {
                return i;
            }
        }
        return CONFIDENCE_EDGES.length;
    }

    // e. 회차마다 결과(의도·맛·요청 종류·1등 분류)가 다른 문장
    private static List<String> determinism(Map<EvalCase, List<JevRun>> byCase) {
        List<String> differing = new ArrayList<>();
        int intentDiffers = 0;
        for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
            Set<String> signatures = entry.getValue().stream().map(JevRun::signature).collect(Collectors.toSet());
            Set<String> intents = entry.getValue().stream()
                    .map(row -> row.answer() == null ? row.status() : row.answer().intent().name())
                    .collect(Collectors.toSet());
            intentDiffers += intents.size() > 1 ? 1 : 0;
            if (signatures.size() > 1) {
                differing.add(entry.getKey().id());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format("[e] 회차마다 결과가 다른 문장: %d / %d (의도가 다른 문장 %d)%s", differing.size(), byCase.size(),
                intentDiffers, differing.isEmpty() ? "" : " · " + String.join(", ", differing)));
        return lines;
    }

    // f. 지연·토큰·비용 (Jev를 부른 회차)
    private static List<String> latencyAndCost(List<JevRun> rows) {
        List<JevRun> judged = rows.stream().filter(JevRun::judged).toList();
        List<Long> first = judged.stream().map(row -> row.judgement().first().call().latencyMs()).toList();
        List<Long> second = judged.stream().filter(row -> row.judgement().hasSecond())
                .map(row -> row.judgement().second().call().latencyMs()).toList();
        List<Long> total = judged.stream().map(row -> row.judgement().totalLatencyMs()).toList();
        long inputTokens = judged.stream().mapToLong(JevIntentEvalManualTest::inputTokens).sum();
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format("[f] Jev 호출 %d회차 (2차까지 %d) · 지연 ms 중앙값/95%%/최대", judged.size(), second.size()));
        lines.add("  1차: " + percentiles(first));
        lines.add("  2차: " + percentiles(second));
        lines.add("  합계: " + percentiles(total));
        lines.add(String.format("  평균 입력 %.0f토큰/메시지 (1차 %.0f, 2차 %.0f) · 전체 입력 %d토큰 · 비용 $%.4f (입력 100만 토큰당 $%.2f)",
                judged.stream().mapToLong(JevIntentEvalManualTest::inputTokens).average().orElse(0),
                judged.stream().mapToLong(row -> orZero(row.judgement().first().call().inputTokens())).average().orElse(0),
                judged.stream().filter(row -> row.judgement().hasSecond())
                        .mapToLong(row -> orZero(row.judgement().second().call().inputTokens())).average().orElse(0),
                inputTokens, inputTokens * USD_PER_MILLION_INPUT / 1_000_000, USD_PER_MILLION_INPUT));
        return lines;
    }

    private static String percentiles(List<Long> values) {
        if (values.isEmpty()) {
            return "-";
        }
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((sorted.size() - 1) / 2) + " / " + sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)
                + " / " + sorted.get(sorted.size() - 1);
    }

    // g. 다수결로 틀린 문장 (틀린 첫 회차의 Jev 답)
    private static List<String> failedCases(Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("[g] 틀린 문장 (id 메시지 (맞은 회차/회차) 기대 → Jev 답 | 틀린 칸):");
        byCase.forEach((evalCase, caseRows) -> {
            long passRuns = caseRows.stream().filter(row -> row.mismatches().isEmpty()).count();
            if (passRuns * 2 > caseRows.size()) {
                return;
            }
            JevRun wrong = caseRows.stream().filter(row -> !row.mismatches().isEmpty()).findFirst().orElse(caseRows.get(0));
            lines.add(String.format("  %s %s (%d/%d) 기대 %s → %s | %s", evalCase.id(), evalCase.message(), passRuns, caseRows.size(),
                    comparedExpect(evalCase), describe(wrong), String.join(" | ", wrong.mismatches())));
        });
        return lines;
    }

    // 기대 값 중 비교하는 칸만 (categories는 kind가 CATEGORY일 때만)
    private static Map<String, Object> comparedExpect(EvalCase evalCase) {
        Map<String, Object> raw = new LinkedHashMap<>();
        evalCase.expect().raw().forEach((field, value) -> {
            if (SHARED.contains(field) || "kind".equals(field)
                    || ("categories".equals(field) && expectsCategory(evalCase.expect().raw()))) {
                raw.put(field, value);
            }
        });
        return raw;
    }

    private static String describe(JevRun row) {
        Answer answer = row.answer();
        if (answer == null) {
            return row.status();
        }
        if (!row.judged()) {
            return answer.intent() + "(" + row.status() + ")";
        }
        JevIntentJudgement.Second second = row.judgement().second();
        return String.format("%s(%.2f) 원함 %s 피함 %s 종류 %s 분류 %s", answer.intent(), row.judgement().first().intentConfidence(),
                new TreeSet<>(answer.wanted()), new TreeSet<>(answer.avoid()),
                second == null ? "-" : second.requestKind() + String.format("(%.2f)", second.requestKindConfidence()),
                second == null ? "-" : second.category() + String.format("(%.2f)", second.categoryConfidence()));
    }

    private static List<String> runLines(List<JevRun> rows) {
        List<String> lines = new ArrayList<>();
        List<String> header = new ArrayList<>(List.of("id", "group", "context", "message", "run", "pass", "mismatch", "status",
                "intent", "jevIntent", "intentConfidence", "intentTop3", "asksQuestion", "fridgeScope", "fridgeScopeConfidence"));
        for (Taste taste : Taste.values()) {
            header.add("taste" + taste.name());
        }
        header.addAll(List.of("requestKind", "requestKindConfidence", "category", "categoryConfidence", "dishNamed", "featureNamed",
                "ingredientNamed", "mealkitWanted", "firstMs", "secondMs", "totalMs", "firstInputTokens", "secondInputTokens",
                "model", "requestIds"));
        lines.add(String.join(",", header));
        for (JevRun row : rows) {
            List<String> mismatches = row.mismatches();
            List<String> cells = new ArrayList<>(List.of(csv(row.evalCase().id()), csv(row.evalCase().group()),
                    csv(row.evalCase().context()), csv(row.evalCase().message()), String.valueOf(row.run()),
                    String.valueOf(mismatches.isEmpty()), csv(String.join(" | ", mismatches)), csv(row.status())));
            JevIntentJudgement judgement = row.judgement();
            JevIntentJudgement.First first = judgement == null ? null : judgement.first();
            JevIntentJudgement.Second second = judgement == null ? null : judgement.second();
            Answer answer = row.answer();
            cells.add(csv(answer == null ? null : answer.intent()));
            cells.add(csv(first == null ? null : first.answeredIntent()));
            cells.add(first == null ? "" : String.valueOf(first.intentConfidence()));
            cells.add(csv(first == null ? null : first.intentProbabilities().entrySet().stream()
                    .sorted(Map.Entry.<ChatIntent, Double>comparingByValue().reversed()).limit(3)
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).toList()));
            cells.add(first == null ? "" : String.valueOf(first.asksQuestion()));
            cells.add(csv(first == null ? null : first.fridgeScope()));
            cells.add(first == null ? "" : String.valueOf(first.fridgeScopeConfidence()));
            for (Taste taste : Taste.values()) {
                cells.add(csv(second == null ? null : second.tastes().get(taste).stance() + "=" + second.tastes().get(taste).confidence()));
            }
            cells.add(csv(second == null ? null : second.requestKind()));
            cells.add(second == null ? "" : String.valueOf(second.requestKindConfidence()));
            cells.add(csv(second == null ? null : second.category()));
            cells.add(second == null ? "" : String.valueOf(second.categoryConfidence()));
            cells.add(second == null ? "" : String.valueOf(second.dishNamed()));
            cells.add(second == null ? "" : String.valueOf(second.featureNamed()));
            cells.add(second == null ? "" : String.valueOf(second.ingredientNamed()));
            cells.add(second == null ? "" : String.valueOf(second.mealkitWanted()));
            cells.add(first == null ? "" : String.valueOf(first.call().latencyMs()));
            cells.add(second == null ? "" : String.valueOf(second.call().latencyMs()));
            cells.add(judgement == null ? String.valueOf(row.ms()) : String.valueOf(judgement.totalLatencyMs()));
            cells.add(first == null ? "" : String.valueOf(orZero(first.call().inputTokens())));
            cells.add(second == null ? "" : String.valueOf(orZero(second.call().inputTokens())));
            cells.add(csv(first == null ? null : first.call().model()));
            cells.add(csv(first == null ? null : first.call().requestId() + (second == null ? "" : " / " + second.call().requestId())));
            lines.add(String.join(",", cells));
        }
        return lines;
    }

    private static List<String> caseLines(Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", "id", "group", "context", "message", "passRuns", "runs", "pass", "mismatches", "expect",
                "intent", "wanted", "avoid", "kind", "category", "intentConfidenceMin", "kindConfidenceMin"));
        byCase.forEach((evalCase, caseRows) -> {
            long passRuns = caseRows.stream().filter(row -> row.mismatches().isEmpty()).count();
            String mismatches = caseRows.stream().flatMap(row -> row.mismatches().stream()).distinct()
                    .collect(Collectors.joining(" | "));
            List<Answer> answers = caseRows.stream().map(JevRun::answer).filter(answer -> answer != null).toList();
            String intentConfidence = caseRows.stream().filter(JevRun::judged)
                    .map(row -> row.judgement().first().intentConfidence()).min(Comparator.naturalOrder())
                    .map(String::valueOf).orElse("");
            String kindConfidence = caseRows.stream().filter(row -> row.judged() && row.judgement().hasSecond())
                    .map(row -> row.judgement().second().requestKindConfidence()).min(Comparator.naturalOrder())
                    .map(String::valueOf).orElse("");
            lines.add(String.join(",", csv(evalCase.id()), csv(evalCase.group()), csv(evalCase.context()), csv(evalCase.message()),
                    String.valueOf(passRuns), String.valueOf(caseRows.size()), String.valueOf(passRuns * 2 > caseRows.size()),
                    csv(mismatches), csv(comparedExpect(evalCase).toString()),
                    csv(mostCommon(answers, answer -> String.valueOf(answer.intent()))),
                    csv(mostCommon(answers, answer -> new TreeSet<>(answer.wanted()).toString())),
                    csv(mostCommon(answers, answer -> new TreeSet<>(answer.avoid()).toString())),
                    csv(mostCommon(answers, answer -> String.valueOf(answer.kind()))),
                    csv(mostCommon(answers, answer -> String.valueOf(answer.category()))),
                    intentConfidence, kindConfidence));
        });
        return lines;
    }

    private static <T> String mostCommon(List<T> values, Function<T, String> key) {
        Map<String, Long> counts = values.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    static <T> boolean majority(List<T> rows, Predicate<T> passes) {
        long passed = rows.stream().filter(passes).count();
        return passed * 2 > rows.size();
    }

    // 틀린 칸 목록("칸: 기대 …")에 그 칸이 없으면 맞음. 1차 실패(status)는 모든 칸이 틀림
    static boolean fieldPasses(List<String> mismatches, String field) {
        return mismatches.stream().noneMatch(mismatch -> mismatch.startsWith(field + ":") || mismatch.startsWith("status:"));
    }

    static List<String> strings(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }

    private static long inputTokens(JevRun row) {
        JevIntentJudgement judgement = row.judgement();
        return orZero(judgement.first().call().inputTokens())
                + (judgement.hasSecond() ? orZero(judgement.second().call().inputTokens()) : 0);
    }

    private static long orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double percent(int part, int whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static String csv(Object value) {
        return ChatIntentEvalManualTest.csv(value);
    }

    /**
     * OpenAI 평가 결과(ChatIntentEvalManualTest의 eval-…-runs.csv)와 같은 id끼리 비교한다.
     * OpenAI는 요청 종류·1등 분류를 내지 않으므로 함께 볼 수 있는 칸(intent·wanted·avoid)만 셋을 나란히 비교하고, 맛 기준 변경은 셋에 같이 적용한다.
     * 보정 전은 AI 원래 값(ai… 칸), 보정 후는 최종 값이다. 단어 차단 등 AI를 부르지 않은 줄은 보정 전도 최종 값으로 본다.
     */
    static final class OpenAiComparison {

        private OpenAiComparison() {
        }

        static List<String> write(Path openAiRuns, Map<EvalCase, List<JevRun>> byCase, Path out) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("");
            if (!Files.exists(openAiRuns)) {
                lines.add("[비교] OpenAI 평가 파일이 없어 건너뜀: " + openAiRuns.toAbsolutePath());
                return lines;
            }
            Map<String, List<Answer[]>> openAi = read(openAiRuns);
            List<String> csvLines = new ArrayList<>();
            csvLines.add(String.join(",", "id", "group", "message", "expect", "beforePass", "afterPass", "jevPass",
                    "beforeMismatches", "afterMismatches", "jevMismatches"));
            Map<String, int[]> fieldCounts = new LinkedHashMap<>();
            SHARED.forEach(field -> fieldCounts.put(field, new int[4]));
            int[] overall = new int[4];
            List<String> jevOnlyWrong = new ArrayList<>();
            // OpenAI 평가 뒤에 평가 세트에 넣은 문장 (OpenAI 결과가 없어 비교 표에 넣지 않는다)
            List<String> noOpenAi = new ArrayList<>();
            for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
                EvalCase evalCase = entry.getKey();
                List<Answer[]> openAiRows = openAi.get(evalCase.id());
                if (openAiRows == null) {
                    boolean jevPass = majority(entry.getValue(), row -> row.mismatches(SHARED).isEmpty());
                    noOpenAi.add(evalCase.id() + " " + evalCase.message() + " → Jev " + (jevPass ? "맞음" : "틀림"));
                    continue;
                }
                Map<String, Object> expect = evalCase.expect().raw();
                List<List<String>> before = openAiRows.stream().map(pair -> mismatches(expect, pair[0], SHARED)).toList();
                List<List<String>> after = openAiRows.stream().map(pair -> mismatches(expect, pair[1], SHARED)).toList();
                List<List<String>> jev = entry.getValue().stream().map(row -> row.mismatches(SHARED)).toList();
                boolean beforePass = majority(before, List::isEmpty);
                boolean afterPass = majority(after, List::isEmpty);
                boolean jevPass = majority(jev, List::isEmpty);
                overall[0]++;
                overall[1] += beforePass ? 1 : 0;
                overall[2] += afterPass ? 1 : 0;
                overall[3] += jevPass ? 1 : 0;
                for (String field : SHARED) {
                    if (!expect.containsKey(field)) {
                        continue;
                    }
                    int[] counts = fieldCounts.get(field);
                    counts[0]++;
                    counts[1] += majority(before, mismatches -> fieldPasses(mismatches, field)) ? 1 : 0;
                    counts[2] += majority(after, mismatches -> fieldPasses(mismatches, field)) ? 1 : 0;
                    counts[3] += majority(jev, mismatches -> fieldPasses(mismatches, field)) ? 1 : 0;
                }
                if (beforePass && afterPass && !jevPass) {
                    jevOnlyWrong.add(evalCase.id() + " " + evalCase.message() + " → " + firstMismatch(jev));
                }
                csvLines.add(String.join(",", csv(evalCase.id()), csv(evalCase.group()), csv(evalCase.message()),
                        csv(comparedExpect(evalCase).toString()), String.valueOf(beforePass), String.valueOf(afterPass),
                        String.valueOf(jevPass), csv(distinct(before)), csv(distinct(after)), csv(distinct(jev))));
            }
            Files.writeString(out, "﻿" + String.join("\n", csvLines) + "\n", StandardCharsets.UTF_8);

            lines.add("[비교] OpenAI 평가 " + openAiRuns.getFileName() + "와 같은 id " + overall[0]
                    + "개 (문장 다수결, 보정 전 / 보정 후 / Jev, 칸 intent·wanted·avoid만, 맛 기준 변경은 셋 모두 적용)");
            lines.add(String.format("  intent·wanted·avoid 모두: %d / %d / %d", overall[1], overall[2], overall[3]));
            fieldCounts.forEach((field, counts) -> lines.add(String.format("  %s (%d문장): %d / %d / %d", field, counts[0],
                    counts[1], counts[2], counts[3])));
            lines.add("OpenAI 결과가 없는 문장 (위 표에서 뺌, intent·wanted·avoid 기준): " + noOpenAi.size());
            noOpenAi.forEach(line -> lines.add("  " + line));
            lines.add("Jev만 틀림 (OpenAI 보정 전·후 모두 맞음): " + jevOnlyWrong.size());
            jevOnlyWrong.forEach(line -> lines.add("  " + line));
            return lines;
        }

        private static String firstMismatch(List<List<String>> runs) {
            return runs.stream().filter(mismatches -> !mismatches.isEmpty()).findFirst()
                    .map(mismatches -> String.join(" | ", mismatches)).orElse("");
        }

        private static String distinct(List<List<String>> runs) {
            return runs.stream().flatMap(List::stream).distinct().collect(Collectors.joining(" | "));
        }

        // id → 회차별 [보정 전, 보정 후]
        private static Map<String, List<Answer[]>> read(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> header = parse(lines.get(0).replace("﻿", ""));
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                index.put(header.get(i), i);
            }
            Map<String, List<Answer[]>> result = new LinkedHashMap<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = parse(line);
                Function<String, String> cell = name -> cells.get(index.get(name));
                Answer after = answer(cell.apply("intent"), cell.apply("wanted"), cell.apply("avoid"));
                Answer before = cell.apply("aiIntent").isBlank() ? after
                        : answer(cell.apply("aiIntent"), cell.apply("aiWanted"), cell.apply("aiAvoid"));
                result.computeIfAbsent(cell.apply("id"), id -> new ArrayList<>()).add(new Answer[]{before, after});
            }
            return result;
        }

        private static Answer answer(String intent, String wanted, String avoid) {
            return new Answer(ChatIntent.valueOf(intent), split(wanted), split(avoid), null, null);
        }

        // csv()가 목록을 " / "로 이었다
        private static Set<String> split(String value) {
            if (value == null || value.isBlank()) {
                return Set.of();
            }
            return Set.of(value.split(" / "));
        }

        // 따옴표로 싼 칸과 ""(따옴표 하나)를 읽는다 (칸 안에 줄바꿈은 없다)
        static List<String> parse(String line) {
            List<String> cells = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (quoted) {
                    if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else if (c == '"') {
                        quoted = false;
                    } else {
                        current.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == ',') {
                    cells.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            cells.add(current.toString());
            return cells;
        }
    }
}

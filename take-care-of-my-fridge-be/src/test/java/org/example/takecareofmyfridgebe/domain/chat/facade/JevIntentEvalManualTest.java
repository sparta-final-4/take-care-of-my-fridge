package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentClassifier;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSafetyBlock;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.CategoryRule;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalCase;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalSet;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.Expect;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.Outcome;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * Jev가 OpenAI 의도 판단만큼 의도·맛·분류를 맞히는지, 믿을 confidence 기준값과 분류 Noul 기준값을 숫자로 본다.
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
 * Jev가 판단하지 않는 칸(요리 이름·키워드·시간·재료 등)은 비교하지 않는다. 비교 칸: intent, wanted, avoid, categories.
 * 맛은 원함·피함 답, 분류는 JevIntentJudgement.CategoryRule(기본값)로 고른 분류를 쓰고, 허용 기준과 정확히 같음 두 가지로 센다.
 * 의도는 직전 추천이 없는데 REFINE·RECIPE_DETAIL이면 RECOMMEND로 본다(ChatIntentFacade 서비스 규칙). 그 밖의 서버 보정은 하지 않는다.
 * 일치율로 실패시키지 않는다. 평가 세트가 비었거나 Jev 호출이 실패했을 때만(결과 파일을 남긴 뒤) 실패한다.
 */
@SpringBootTest(classes = JevIntentEvalManualTest.class)
@EnableConfigurationProperties(ChatSafetyProperties.class)
@EnabledIfEnvironmentVariable(named = "JEV_INTENT_EVAL", matches = "true")
class JevIntentEvalManualTest {

    private static final Path OUT_DIR = Path.of("build", "reports", "chat-intent-eval");
    // Jev가 답하는 비교 칸. 밀키트는 평가 세트에 기대 칸이 없어 비교하지 않는다
    private static final List<String> COMPARED = List.of("intent", "wanted", "avoid", "categories");
    // 분류 고르기 규칙. 운영 비교 로그와 같은 기본값이고, 아래 조합별 표로 다시 센다
    static final CategoryRule RULE = CategoryRule.DEFAULT;
    private static final double[] SWEEP_TOP = {0.6, 0.7, 0.8};
    private static final double[] SWEEP_GAP = {0.1, 0.15, 0.2};
    private static final int[] SWEEP_WIDEST = {3, 4, 5};
    // 직전 추천이 없을 때 이어지는 말(REFINE·RECIPE_DETAIL)은 새 추천으로 본다 (ChatIntentFacade 서비스 규칙)
    private static final Set<ChatIntent> FOLLOW_UP_INTENTS = Set.of(ChatIntent.REFINE, ChatIntent.RECIPE_DETAIL);
    private static final double[] CONFIDENCE_EDGES = {0.95, 0.9, 0.8, 0.7};
    // 입력 100만 토큰당 비용 (USD)
    private static final double USD_PER_MILLION_INPUT = 0.04;
    // 2차(추천 조건)를 묻는 의도 (JevIntentClassifier와 같다)
    private static final Set<ChatIntent> SECOND_INTENTS = Set.of(ChatIntent.RECOMMEND, ChatIntent.REFINE);

    @Autowired
    private ChatSafetyProperties safety;

    @Test
    @DisplayName("평가 세트를 실제 Jev로 돌려 의도·맛·분류를 기대와 비교하고 confidence·분류 기준값별 결과를 CSV로 남긴다")
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
        write(set, cases, rows, runs, properties.model());

        // Jev 호출이 실패한 회차는 결과가 의미 없으므로, 파일을 남긴 뒤 실패시킨다
        List<JevRun> failed = rows.stream().filter(row -> row.status().endsWith("FAILED")).toList();
        assertThat(failed).as("Jev 호출 실패 %d번 (%s)", failed.size(),
                failed.stream().map(row -> row.evalCase().id() + " " + row.status()).collect(Collectors.joining(", "))).isEmpty();
    }

    private JevRun runOnce(EvalSet set, JevIntentClassifier classifier, List<String> categories, EvalCase evalCase, int run) {
        Expect compared = compared(evalCase.expect());
        Optional<ChatSafetyBlock> block = SafetyWordChecker.check(evalCase.message(), safety.words());
        if (block.isPresent()) {
            ChatIntent intent = block.get() == ChatSafetyBlock.SELF_HARM ? ChatIntent.SELF_HARM : ChatIntent.BLOCKED;
            return new JevRun(evalCase, compared, run, "WORD_" + block.get(), intent, null, false, 0);
        }
        String followUp = ChatIntentFacade.followUp(set.context(evalCase.context()));
        long startedAt = System.nanoTime();
        Optional<JevIntentJudgement> judgement = classifier.classify(evalCase.message(), categories, followUp);
        long ms = (System.nanoTime() - startedAt) / 1_000_000;
        String status = judgement.map(found -> SECOND_INTENTS.contains(found.first().intent()) && !found.hasSecond()
                ? "SECOND_FAILED" : "OK").orElse("FIRST_FAILED");
        return new JevRun(evalCase, compared, run, status, null, judgement.orElse(null), followUp != null, ms);
    }

    private static Set<String> onlyIds() {
        String only = System.getenv("JEV_INTENT_EVAL_ONLY");
        if (only == null || only.isBlank()) {
            return Set.of();
        }
        return Set.of(only.split("\\s*,\\s*"));
    }

    // 기대 값에서 Jev가 답하는 칸만 남긴다 (적은 순서 그대로)
    static Expect compared(Expect expect) {
        Map<String, Object> raw = new LinkedHashMap<>();
        expect.raw().forEach((field, value) -> {
            if (COMPARED.contains(field)) {
                raw.put(field, value);
            }
        });
        return new Expect(raw);
    }

    /**
     * 기대와 다른 칸. 분류는 두 가지로 센다.
     * <ul>
     *   <li>정확히 같음(allowed=false): 고른 분류 집합이 기대와 같다</li>
     *   <li>허용(allowed=true): 고른 분류가 하나 이상이고 모두 기대 분류 안에 있다. 기대 밖이 하나라도 있으면 틀림.
     *       기대가 비었으면 고르지 않아야 맞다</li>
     * </ul>
     */
    static List<String> mismatches(Expect compared, Outcome outcome, EvalSet set, boolean allowed) {
        if (!allowed || !compared.raw().containsKey("categories")) {
            return compared.mismatches(outcome, set);
        }
        Map<String, Object> rest = new LinkedHashMap<>(compared.raw());
        Object expected = rest.remove("categories");
        List<String> result = new ArrayList<>(new Expect(rest).mismatches(outcome, set));
        if (!categoriesAllowed(Set.copyOf(strings(expected)), outcome.categories())) {
            result.add("categories: 기대 " + expected + " 안, 실제 " + outcome.categories());
        }
        return result;
    }

    static boolean categoriesAllowed(Set<String> expected, Set<String> actual) {
        if (expected.isEmpty()) {
            return actual.isEmpty();
        }
        return !actual.isEmpty() && expected.containsAll(actual);
    }

    /**
     * 한 번 돌린 결과.
     *
     * @param status       OK / FIRST_FAILED / SECOND_FAILED / WORD_{차단 종류}
     * @param blockedIntent 단어 차단에 걸렸을 때의 의도 (Jev를 부르지 않음)
     * @param judgement    Jev 결과 (단어 차단·1차 실패면 null)
     * @param followUp     직전 추천 정보를 넘겼는지
     * @param ms           classify 전체 시간
     */
    record JevRun(EvalCase evalCase, Expect compared, int run, String status, ChatIntent blockedIntent,
                  JevIntentJudgement judgement, boolean followUp, long ms) {

        boolean judged() {
            return judgement != null;
        }

        // 분류 고르기 규칙 rule로 본 Jev 결과. 1차 실패면 null
        Outcome outcome(CategoryRule rule) {
            if (blockedIntent != null) {
                return Outcome.blocked(blockedIntent);
            }
            if (judgement == null) {
                return null;
            }
            JevIntentJudgement.Second second = judgement.second();
            return new Outcome(intent(),
                    second == null ? Set.of() : tastes(second, TasteStance.WANTED),
                    second == null ? Set.of() : tastes(second, TasteStance.AVOIDED),
                    picked(rule),
                    List.of(), null, null, null, Set.of(), Set.of(), null, judgement.first().fridgeScope().name(),
                    Set.of(), null, null, null, null);
        }

        // 직전 추천이 없는데 이어지는 말(REFINE·RECIPE_DETAIL)이면 새 추천으로 본다
        ChatIntent intent() {
            ChatIntent intent = judgement.first().intent();
            return !followUp && FOLLOW_UP_INTENTS.contains(intent) ? ChatIntent.RECOMMEND : intent;
        }

        Set<String> picked(CategoryRule rule) {
            if (judgement == null || judgement.second() == null) {
                return Set.of();
            }
            return new LinkedHashSet<>(rule.pick(judgement.second().categories()));
        }

        // 분류는 allowed면 허용 기준, 아니면 정확히 같음으로 본다
        List<String> mismatches(EvalSet set, CategoryRule rule, boolean allowed) {
            Outcome outcome = outcome(rule);
            if (outcome == null) {
                return List.of("status: " + status);
            }
            return JevIntentEvalManualTest.mismatches(compared, outcome, set, allowed);
        }

        // 기본 규칙·허용 기준 (요약·CSV의 일치 판단)
        List<String> mismatches(EvalSet set) {
            return mismatches(set, RULE, true);
        }

        // 결과를 비교할 때 쓰는 한 줄 (의도·맛·고른 분류)
        String signature() {
            Outcome outcome = outcome(RULE);
            return outcome == null ? status
                    : outcome.intent() + " " + new TreeSet<>(outcome.wanted()) + " " + new TreeSet<>(outcome.avoid())
                    + " " + new TreeSet<>(outcome.categories());
        }

        private static Set<String> tastes(JevIntentJudgement.Second second, TasteStance stance) {
            return second.tastes().entrySet().stream()
                    .filter(entry -> entry.getValue().stance() == stance)
                    .map(entry -> entry.getKey().name())
                    .collect(Collectors.toSet());
        }
    }

    private void write(EvalSet set, List<EvalCase> cases, List<JevRun> rows, int runs, String model) throws IOException {
        Files.createDirectories(OUT_DIR);
        String label = Optional.ofNullable(System.getenv("JEV_INTENT_EVAL_LABEL")).map(String::strip)
                .filter(value -> !value.isEmpty()).map(value -> value.replaceAll("[^A-Za-z0-9_-]", "_")).orElse(null);
        String stamp = "jev-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + (label == null ? "" : "-" + label);
        Map<EvalCase, List<JevRun>> byCase = new LinkedHashMap<>();
        cases.forEach(evalCase -> byCase.put(evalCase, rows.stream().filter(row -> row.evalCase() == evalCase).toList()));

        List<String> summary = new ArrayList<>();
        summary.add("Jev 상담 의도 평가 " + stamp + " · 모델 " + model + " · 문장 " + cases.size() + "개 × " + runs
                + "번 (다수결) · 분류 고르기 " + RULE);
        summary.add("비교 칸: " + String.join(", ", COMPARED) + " (밀키트는 평가 세트에 기대 칸이 없어 비교하지 않음)");
        summary.add("분류는 허용 기준(고른 분류가 하나 이상이고 모두 기대 안)으로 맞음을 센다. "
                + "직전 추천이 없는데 REFINE·RECIPE_DETAIL이면 RECOMMEND로 본다");
        summary.addAll(accuracy(set, byCase));
        summary.addAll(confidenceBuckets(set, rows));
        summary.addAll(ruleSweep(set, byCase));
        summary.addAll(determinism(byCase));
        summary.addAll(latencyAndCost(rows));
        summary.addAll(failedCases(set, byCase));

        Path compareFile = null;
        String openAiRuns = System.getenv("OPENAI_EVAL_RUNS_CSV");
        if (openAiRuns != null && !openAiRuns.isBlank()) {
            compareFile = OUT_DIR.resolve(stamp + "-compare.csv");
            summary.addAll(OpenAiComparison.write(Path.of(openAiRuns.strip()), set, byCase, compareFile));
        }

        Path runsFile = OUT_DIR.resolve(stamp + "-runs.csv");
        Path casesFile = OUT_DIR.resolve(stamp + "-cases.csv");
        Path summaryFile = OUT_DIR.resolve(stamp + "-summary.txt");
        // 엑셀에서 한글이 깨지지 않게 BOM을 붙인다
        Files.writeString(runsFile, "﻿" + String.join("\n", runLines(set, rows)) + "\n", StandardCharsets.UTF_8);
        Files.writeString(casesFile, "﻿" + String.join("\n", caseLines(set, byCase)) + "\n", StandardCharsets.UTF_8);
        Files.writeString(summaryFile, String.join("\n", summary) + "\n", StandardCharsets.UTF_8);
        summary.forEach(System.out::println);
        System.out.println("결과: " + runsFile.toAbsolutePath() + ", " + casesFile.getFileName() + ", " + summaryFile.getFileName()
                + (compareFile == null ? "" : ", " + compareFile.getFileName()));
    }

    // a. 전체·묶음별(다수결)·칸별 일치율
    private static List<String> accuracy(EvalSet set, Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        Map<String, int[]> byGroup = new LinkedHashMap<>();
        int passed = 0;
        for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
            boolean pass = majority(entry.getValue(), row -> row.mismatches(set).isEmpty());
            passed += pass ? 1 : 0;
            int[] counts = byGroup.computeIfAbsent(entry.getKey().group(), group -> new int[2]);
            counts[0] += pass ? 1 : 0;
            counts[1]++;
        }
        lines.add("");
        lines.add("[a] 일치율 (문장 다수결)");
        lines.add(String.format("전체 일치: %d / %d (%.1f%%)", passed, byCase.size(), percent(passed, byCase.size())));
        byGroup.forEach((group, counts) -> lines.add(String.format("  %s: %d / %d", group, counts[0], counts[1])));
        lines.add("칸별 (그 칸을 기대한 문장만, 분류는 허용 기준):");
        for (String field : COMPARED) {
            int[] counts = fieldCount(set, byCase, field, RULE, true);
            lines.add(String.format("  %s: %d / %d (%.1f%%)", field, counts[0], counts[1], percent(counts[0], counts[1])));
        }
        int[] exact = fieldCount(set, byCase, "categories", RULE, false);
        lines.add(String.format("  categories 정확히 같음: %d / %d (%.1f%%)", exact[0], exact[1], percent(exact[0], exact[1])));
        return lines;
    }

    // 그 칸을 기대한 문장 중 다수결로 맞은 수 [맞음, 전체]
    private static int[] fieldCount(EvalSet set, Map<EvalCase, List<JevRun>> byCase, String field, CategoryRule rule,
                                    boolean allowed) {
        int total = 0;
        int ok = 0;
        for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
            if (!entry.getKey().expect().raw().containsKey(field)) {
                continue;
            }
            total++;
            ok += majority(entry.getValue(), row -> fieldPasses(row.mismatches(set, rule, allowed), field)) ? 1 : 0;
        }
        return new int[]{ok, total};
    }

    // b. intent confidence 구간별 회차 수와 의도 정답률 (Jev를 부른 회차, 의도를 기대한 문장만)
    private static List<String> confidenceBuckets(EvalSet set, List<JevRun> rows) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("[b] intent confidence 구간별 의도 정답률 (회차 기준, 단어 차단·1차 실패 제외)");
        String[] names = {"≥0.95", "0.9~0.95", "0.8~0.9", "0.7~0.8", "<0.7"};
        int[] total = new int[names.length];
        int[] ok = new int[names.length];
        List<Set<String>> caseIds = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            caseIds.add(new LinkedHashSet<>());
        }
        for (JevRun row : rows) {
            if (!row.judged() || !row.compared().raw().containsKey("intent")) {
                continue;
            }
            int bucket = bucket(row.judgement().first().intentConfidence());
            total[bucket]++;
            ok[bucket] += fieldPasses(row.mismatches(set), "intent") ? 1 : 0;
            caseIds.get(bucket).add(row.evalCase().id());
        }
        for (int i = 0; i < names.length; i++) {
            lines.add(String.format("  %-9s 회차 %3d (문장 %3d) · 의도 정답 %3d (%.1f%%)", names[i], total[i], caseIds.get(i).size(),
                    ok[i], percent(ok[i], total[i])));
        }
        // 기준값을 정할 때: 이 값 이상만 믿으면 몇 %를 쓰고 그중 몇 %가 맞는가
        int all = 0;
        for (int count : total) {
            all += count;
        }
        int keptTotal = 0;
        int keptOk = 0;
        for (int i = 0; i < CONFIDENCE_EDGES.length; i++) {
            keptTotal += total[i];
            keptOk += ok[i];
            lines.add(String.format("  confidence ≥ %.2f만 믿으면: 회차의 %.1f%% 사용, 그중 정답 %.1f%%", CONFIDENCE_EDGES[i],
                    percent(keptTotal, all), percent(keptOk, keptTotal)));
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

    // c. 분류 고르기 조합(top·gap·widest, floor는 기본값)별 분류 일치 (분류를 기대한 문장, 다수결). API를 다시 부르지 않고 저장된 확률로 센다
    private static List<String> ruleSweep(EvalSet set, Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format("[c] 분류 고르기 조합별 (floor %.2f, 분류를 기대한 문장 다수결, 허용 / 정확히 같음 / 고른 분류 수 평균)",
                RULE.floor()));
        lines.add("  top   gap   widest | 허용          | 정확히 같음   | 평균 개수");
        List<JevRun> targets = byCase.values().stream().flatMap(List::stream)
                .filter(row -> row.judged() && row.compared().raw().containsKey("categories"))
                .toList();
        for (double top : SWEEP_TOP) {
            for (double gap : SWEEP_GAP) {
                for (int widest : SWEEP_WIDEST) {
                    CategoryRule rule = new CategoryRule(top, gap, RULE.floor(), widest);
                    int[] allowed = fieldCount(set, byCase, "categories", rule, true);
                    int[] exact = fieldCount(set, byCase, "categories", rule, false);
                    double size = targets.stream().mapToInt(row -> row.picked(rule).size()).average().orElse(0);
                    lines.add(String.format("  %.2f  %.2f  %d      | %2d / %d (%5.1f%%) | %2d / %d (%5.1f%%) | %.2f%s", top, gap, widest,
                            allowed[0], allowed[1], percent(allowed[0], allowed[1]), exact[0], exact[1], percent(exact[0], exact[1]),
                            size, rule.equals(RULE) ? "  (기본)" : ""));
                }
            }
        }
        return lines;
    }

    // d. 회차마다 결과(의도·맛·기준값 분류)가 다른 문장
    private static List<String> determinism(Map<EvalCase, List<JevRun>> byCase) {
        List<String> differing = new ArrayList<>();
        int intentDiffers = 0;
        for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
            Set<String> signatures = entry.getValue().stream().map(JevRun::signature).collect(Collectors.toSet());
            Set<String> intents = entry.getValue().stream()
                    .map(row -> row.outcome(RULE) == null ? row.status() : row.outcome(RULE).intent().name())
                    .collect(Collectors.toSet());
            intentDiffers += intents.size() > 1 ? 1 : 0;
            if (signatures.size() > 1) {
                differing.add(entry.getKey().id());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format("[d] 회차마다 결과가 다른 문장: %d / %d (의도가 다른 문장 %d)%s", differing.size(), byCase.size(),
                intentDiffers, differing.isEmpty() ? "" : " · " + String.join(", ", differing)));
        return lines;
    }

    // e. 지연·토큰·비용 (Jev를 부른 회차)
    private static List<String> latencyAndCost(List<JevRun> rows) {
        List<JevRun> judged = rows.stream().filter(JevRun::judged).toList();
        List<Long> first = judged.stream().map(row -> row.judgement().first().call().latencyMs()).toList();
        List<Long> second = judged.stream().filter(row -> row.judgement().hasSecond())
                .map(row -> row.judgement().second().call().latencyMs()).toList();
        List<Long> total = judged.stream().map(row -> row.judgement().totalLatencyMs()).toList();
        long inputTokens = judged.stream().mapToLong(JevIntentEvalManualTest::inputTokens).sum();
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format("[e] Jev 호출 %d회차 (2차까지 %d) · 지연 ms 중앙값/95%%/최대", judged.size(), second.size()));
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

    // f. 다수결로 틀린 문장 (틀린 첫 회차의 Jev 답)
    private static List<String> failedCases(EvalSet set, Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("[f] 틀린 문장 (id 메시지 (맞은 회차/회차) 기대 → Jev 답):");
        byCase.forEach((evalCase, caseRows) -> {
            long passRuns = caseRows.stream().filter(row -> row.mismatches(set).isEmpty()).count();
            if (passRuns * 2 > caseRows.size()) {
                return;
            }
            JevRun wrong = caseRows.stream().filter(row -> !row.mismatches(set).isEmpty()).findFirst()
                    .orElse(caseRows.get(0));
            lines.add(String.format("  %s %s (%d/%d) 기대 %s → %s", evalCase.id(), evalCase.message(), passRuns, caseRows.size(),
                    wrong.compared().raw(), answer(wrong)));
        });
        return lines;
    }

    private static String answer(JevRun row) {
        Outcome outcome = row.outcome(RULE);
        if (outcome == null) {
            return row.status();
        }
        String confidence = row.judged() ? String.format("(%.2f)", row.judgement().first().intentConfidence()) : "(" + row.status() + ")";
        return outcome.intent() + confidence + " 원함 " + new TreeSet<>(outcome.wanted()) + " 피함 " + new TreeSet<>(outcome.avoid())
                + " 분류 " + categoryProbabilities(row, 0.2);
    }

    // 확률이 floor 이상인 분류 (확률 높은 순)
    private static String categoryProbabilities(JevRun row, double floor) {
        if (!row.judged() || !row.judgement().hasSecond()) {
            return "[]";
        }
        return row.judgement().second().categories().entrySet().stream()
                .filter(entry -> entry.getValue() >= floor)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static List<String> runLines(EvalSet set, List<JevRun> rows) {
        List<String> lines = new ArrayList<>();
        List<String> header = new ArrayList<>(List.of("id", "group", "context", "message", "run", "pass", "mismatch", "status",
                "intent", "intentConfidence", "intentTop3", "asksQuestion", "fridgeScope", "fridgeScopeConfidence"));
        for (Taste taste : Taste.values()) {
            header.add("taste" + taste.name());
        }
        header.addAll(List.of("categoriesPicked", "categoryProbabilities", "dishNamed", "featureNamed",
                "ingredientNamed", "mealkitWanted", "firstMs", "secondMs", "totalMs", "firstInputTokens", "secondInputTokens",
                "model", "requestIds"));
        lines.add(String.join(",", header));
        for (JevRun row : rows) {
            List<String> mismatches = row.mismatches(set);
            List<String> cells = new ArrayList<>(List.of(csv(row.evalCase().id()), csv(row.evalCase().group()),
                    csv(row.evalCase().context()), csv(row.evalCase().message()), String.valueOf(row.run()),
                    String.valueOf(mismatches.isEmpty()), csv(String.join(" | ", mismatches)), csv(row.status())));
            JevIntentJudgement judgement = row.judgement();
            JevIntentJudgement.First first = judgement == null ? null : judgement.first();
            JevIntentJudgement.Second second = judgement == null ? null : judgement.second();
            cells.add(csv(first == null ? row.blockedIntent() : first.intent()));
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
            cells.add(csv(row.picked(RULE)));
            cells.add(csv(second == null ? null : second.categories().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).toList()));
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

    private static List<String> caseLines(EvalSet set, Map<EvalCase, List<JevRun>> byCase) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", "id", "group", "context", "message", "passRuns", "runs", "pass", "mismatches", "expect",
                "intent", "wanted", "avoid", "categories", "intentConfidenceMin"));
        byCase.forEach((evalCase, caseRows) -> {
            long passRuns = caseRows.stream().filter(row -> row.mismatches(set).isEmpty()).count();
            String mismatches = caseRows.stream().flatMap(row -> row.mismatches(set).stream()).distinct()
                    .collect(Collectors.joining(" | "));
            List<Outcome> outcomes = caseRows.stream().map(row -> row.outcome(RULE)).filter(outcome -> outcome != null).toList();
            String confidence = caseRows.stream().filter(JevRun::judged)
                    .map(row -> row.judgement().first().intentConfidence()).min(Comparator.naturalOrder())
                    .map(String::valueOf).orElse("");
            lines.add(String.join(",", csv(evalCase.id()), csv(evalCase.group()), csv(evalCase.context()), csv(evalCase.message()),
                    String.valueOf(passRuns), String.valueOf(caseRows.size()), String.valueOf(passRuns * 2 > caseRows.size()),
                    csv(mismatches), csv(compared(evalCase.expect()).raw().toString()),
                    csv(mostCommon(outcomes, outcome -> String.valueOf(outcome.intent()))),
                    csv(mostCommon(outcomes, outcome -> new TreeSet<>(outcome.wanted()).toString())),
                    csv(mostCommon(outcomes, outcome -> new TreeSet<>(outcome.avoid()).toString())),
                    csv(mostCommon(outcomes, outcome -> new TreeSet<>(outcome.categories()).toString())),
                    confidence));
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

    // 틀린 칸 목록(Expect.mismatches의 "칸: 기대 …")에 그 칸이 없으면 맞음. 1차 실패(status)는 모든 칸이 틀림
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
     * 보정 전은 AI 원래 값(ai… 칸), 보정 후는 최종 값이다. 단어 차단 등 AI를 부르지 않은 줄은 보정 전도 최종 값으로 본다.
     */
    static final class OpenAiComparison {

        private OpenAiComparison() {
        }

        static List<String> write(Path openAiRuns, EvalSet set, Map<EvalCase, List<JevRun>> byCase, Path out) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("");
            if (!Files.exists(openAiRuns)) {
                lines.add("[비교] OpenAI 평가 파일이 없어 건너뜀: " + openAiRuns.toAbsolutePath());
                return lines;
            }
            Map<String, List<Outcome[]>> openAi = read(openAiRuns);
            List<String> csvLines = new ArrayList<>();
            csvLines.add(String.join(",", "id", "group", "message", "expect", "beforePass", "afterPass", "jevPass",
                    "beforeMismatches", "afterMismatches", "jevMismatches"));
            Map<String, int[]> fieldCounts = new LinkedHashMap<>();
            COMPARED.forEach(field -> fieldCounts.put(field, new int[4]));
            int[] overall = new int[4];
            int[] exactOverall = new int[3];
            List<String> jevOnlyWrong = new ArrayList<>();
            List<String> beforeOnlyWrong = new ArrayList<>();
            List<String> afterOnlyRight = new ArrayList<>();
            for (Map.Entry<EvalCase, List<JevRun>> entry : byCase.entrySet()) {
                EvalCase evalCase = entry.getKey();
                List<Outcome[]> openAiRows = openAi.get(evalCase.id());
                if (openAiRows == null) {
                    continue;
                }
                Expect compared = compared(evalCase.expect());
                List<List<String>> before = openAiRows.stream().map(pair -> mismatches(compared, pair[0], set, true)).toList();
                List<List<String>> after = openAiRows.stream().map(pair -> mismatches(compared, pair[1], set, true)).toList();
                List<List<String>> jev = entry.getValue().stream().map(row -> row.mismatches(set)).toList();
                boolean beforePass = majority(before, List::isEmpty);
                boolean afterPass = majority(after, List::isEmpty);
                boolean jevPass = majority(jev, List::isEmpty);
                overall[0]++;
                overall[1] += beforePass ? 1 : 0;
                overall[2] += afterPass ? 1 : 0;
                overall[3] += jevPass ? 1 : 0;
                exactOverall[0] += majority(openAiRows, pair -> mismatches(compared, pair[0], set, false).isEmpty()) ? 1 : 0;
                exactOverall[1] += majority(openAiRows, pair -> mismatches(compared, pair[1], set, false).isEmpty()) ? 1 : 0;
                exactOverall[2] += majority(entry.getValue(), row -> row.mismatches(set, RULE, false).isEmpty()) ? 1 : 0;
                for (String field : COMPARED) {
                    if (!compared.raw().containsKey(field)) {
                        continue;
                    }
                    int[] counts = fieldCounts.get(field);
                    counts[0]++;
                    counts[1] += majority(before, mismatches -> fieldPasses(mismatches, field)) ? 1 : 0;
                    counts[2] += majority(after, mismatches -> fieldPasses(mismatches, field)) ? 1 : 0;
                    counts[3] += majority(jev, mismatches -> fieldPasses(mismatches, field)) ? 1 : 0;
                }
                String label = evalCase.id() + " " + evalCase.message();
                if (beforePass && afterPass && !jevPass) {
                    jevOnlyWrong.add(label + " → " + firstMismatch(jev));
                }
                if (!beforePass && afterPass && jevPass) {
                    beforeOnlyWrong.add(label + " → " + firstMismatch(before));
                }
                if (!beforePass && afterPass && !jevPass) {
                    afterOnlyRight.add(label + " → 보정 전 " + firstMismatch(before) + " / Jev " + firstMismatch(jev));
                }
                csvLines.add(String.join(",", csv(evalCase.id()), csv(evalCase.group()), csv(evalCase.message()),
                        csv(compared.raw().toString()), String.valueOf(beforePass), String.valueOf(afterPass), String.valueOf(jevPass),
                        csv(distinct(before)), csv(distinct(after)), csv(distinct(jev))));
            }
            Files.writeString(out, "﻿" + String.join("\n", csvLines) + "\n", StandardCharsets.UTF_8);

            lines.add("[비교] OpenAI 평가 " + openAiRuns.getFileName() + "와 같은 id " + overall[0]
                    + "개 (문장 다수결, 보정 전 / 보정 후 / Jev, 분류는 셋 모두 허용 기준)");
            lines.add(String.format("  전체: %d / %d / %d", overall[1], overall[2], overall[3]));
            lines.add(String.format("  전체 (분류 정확히 같음): %d / %d / %d", exactOverall[0], exactOverall[1], exactOverall[2]));
            fieldCounts.forEach((field, counts) -> lines.add(String.format("  %s (%d문장): %d / %d / %d", field, counts[0],
                    counts[1], counts[2], counts[3])));
            lines.add("Jev만 틀림 (OpenAI 보정 전·후 모두 맞음): " + jevOnlyWrong.size());
            jevOnlyWrong.forEach(line -> lines.add("  " + line));
            lines.add("OpenAI 보정 전만 틀림 (보정 후·Jev 맞음): " + beforeOnlyWrong.size());
            beforeOnlyWrong.forEach(line -> lines.add("  " + line));
            lines.add("보정 후만 맞음 (보정 전·Jev 틀림): " + afterOnlyRight.size());
            afterOnlyRight.forEach(line -> lines.add("  " + line));
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
        private static Map<String, List<Outcome[]>> read(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> header = parse(lines.get(0).replace("﻿", ""));
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                index.put(header.get(i), i);
            }
            Map<String, List<Outcome[]>> result = new LinkedHashMap<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = parse(line);
                Function<String, String> cell = name -> cells.get(index.get(name));
                Outcome after = outcome(cell.apply("intent"), cell.apply("wanted"), cell.apply("avoid"), cell.apply("categories"));
                Outcome before = cell.apply("aiIntent").isBlank() ? after
                        : outcome(cell.apply("aiIntent"), cell.apply("aiWanted"), cell.apply("aiAvoid"), cell.apply("aiCategories"));
                result.computeIfAbsent(cell.apply("id"), id -> new ArrayList<>()).add(new Outcome[]{before, after});
            }
            return result;
        }

        private static Outcome outcome(String intent, String wanted, String avoid, String categories) {
            return new Outcome(ChatIntent.valueOf(intent), split(wanted), split(avoid), split(categories), List.of(), null, null,
                    null, Set.of(), Set.of(), null, null, Set.of(), null, null, null, null);
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

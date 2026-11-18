package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentClassifier;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.ai.OpenAiSimilarDishGenerator;
import org.example.takecareofmyfridgebe.domain.chat.ai.SimilarDishGenerator;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishes;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalSet;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.Expect;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.Outcome;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.UsageRecorder;
import org.example.takecareofmyfridgebe.domain.chat.intent.JevIntentResolver;
import org.example.takecareofmyfridgebe.domain.chat.service.SimilarDishStore;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.TypeSafeConfig;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.global.config.TypeSafeExecutor;
import org.example.takecareofmyfridgebe.global.config.ai.ChatClientConfig;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * ChatIntentEvalManualTest의 Jev 부분 (의도 판단은 늘 Jev로 정한다). 설계: docs/testing/jev-on-design.md 8절
 * <ul>
 *   <li>실제 JevIntentRunner·JevIntentClassifier(TypeSafe)·JevIntentResolver·OpenAiSimilarDishGenerator(넓은 요청 프롬프트)를 만든다.
 *       넓은 요청 요리 이름 캐시는 메모리에 둔다(운영 Redis처럼 같은 표현이면 다시 부르지 않는다. 그래서 문장마다 첫 회차만 AI를 부른다)</li>
 *   <li>회차마다 정한 방법(decision), Jev 판단(의도별 확률 전체, 맛 5개 답 포함), 쓴 요청 종류·값, 넓은 요청 AI 호출 여부·시간·토큰을 남긴다</li>
 *   <li>자해 기준 세트(OPENAI_INTENT_EVAL_SET=safety)면 요약에 문장별 Jev 자해 확률(회차 최소~최대)·1등 의도를 남긴다</li>
 *   <li>기대 kind가 넓은 요청(BROAD)인 문장은 분류·키워드 기대값 대신 "Jev로 정했고
 *       쓴 요청 종류가 BROAD이며 요리 이름이 비어 있지 않음"으로 비교한다.
 *       요청 종류 일치(kind)는 따로 세고 일치 판단에는 넣지 않는다(all-on과 같은 칸으로 비교하려고)</li>
 * </ul>
 */
final class ChatIntentEvalJevOn {

    // 의도별 확률 칸 이름 앞말 (jevProb_SELF_HARM), 맛 답 칸 이름 앞말 (jevTaste_SPICY)
    static final String PROBABILITY_PREFIX = "jevProb_";
    static final String TASTE_PREFIX = "jevTaste_";

    // 넓은 요청 기대값을 대신하는 칸 (분류·키워드 기대값은 비교하지 않는다)
    private static final Set<String> BROAD_REPLACED = Set.of("categories", "keywordsEmpty", "keywordsAny");
    private static final String MODEL = "gpt-4o-mini";
    // Jev 입력 100만 토큰당 비용 (USD, JevIntentEvalManualTest와 같다)
    private static final double JEV_USD_PER_MILLION_INPUT = 0.04;

    private final RecordingJevRunner runner;
    private final RecordingDishGenerator broadGenerator;
    private final JevIntentResolver resolver;
    private final TypeSafeExecutor executor;

    private ChatIntentEvalJevOn(RecordingJevRunner runner, RecordingDishGenerator broadGenerator, JevIntentResolver resolver,
                                TypeSafeExecutor executor) {
        this.runner = runner;
        this.broadGenerator = broadGenerator;
        this.resolver = resolver;
        this.executor = executor;
    }

    static ChatIntentEvalJevOn create(ChatWordProperties words) {
        String typeSafeKey = System.getenv("TYPESAFE_API_KEY");
        // 키가 없으면 실패하지 않고 건너뛴다 (없으면 모든 메시지가 서버 답이 되어 평가 뜻이 없다)
        assumeThat(typeSafeKey).as("의도 평가에는 TYPESAFE_API_KEY가 필요함").isNotBlank();
        TypeSafeProperties properties = new TypeSafeProperties(null, typeSafeKey, null, null);
        TypeSafeClient client = new TypeSafeClient(new TypeSafeConfig().typeSafeRestClient(properties), properties);
        TypeSafeExecutor executor = new TypeSafeExecutor(2, 4);
        RecordingJevRunner runner = new RecordingJevRunner(new JevIntentClassifier(client), client, executor);

        UsageRecorder broadUsage = new UsageRecorder();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(System.getenv("OPENAI_INTENT_EVAL_KEY")).model(MODEL).build())
                .build();
        ChatClient chatClient = new ChatClientConfig().chatSimilarDishChatClient(ChatClient.builder(model), broadUsage);
        RecordingDishGenerator broadGenerator = new RecordingDishGenerator(new OpenAiSimilarDishGenerator(chatClient, broadUsage,
                new ClassPathResource("prompts/chat-similar-dishes.txt")), broadUsage);
        JevIntentResolver resolver = new JevIntentResolver(new SimilarDishFinder(new MemoryDishStore(), broadGenerator), words);
        return new ChatIntentEvalJevOn(runner, broadGenerator, resolver, executor);
    }

    JevIntentRunner runner() {
        return runner;
    }

    JevIntentResolver resolver() {
        return resolver;
    }

    void close() {
        executor.close();
    }

    // 회차마다 지운다
    void clear() {
        runner.judgement = null;
        runner.decision = null;
        broadGenerator.called = false;
        broadGenerator.ms = 0;
        broadGenerator.input = 0;
        broadGenerator.output = 0;
    }

    Info info() {
        JevDecision decision = runner.decision;
        return new Info(decision == null ? null : decision.decision(), runner.judgement,
                decision == null ? null : decision.usedKind(), decision == null ? List.of() : decision.usedValues(),
                decision == null ? null : decision.reason(),
                broadGenerator.called, broadGenerator.ms, broadGenerator.input, broadGenerator.output);
    }

    /**
     * 한 회차의 Jev 쪽 기록.
     *
     * @param decision    정한 방법 (OpenAI 의도 판단이 실패해 비교 로그를 남기지 않았으면 null)
     * @param judgement   Jev 판단 (실패·제한 시간이면 null)
     * @param usedKind    Jev로 정했을 때 쓴 요청 종류
     * @param usedValues  그 요청 종류로 쓴 값 (넓은 요청이면 요리 이름)
     * @param usedReason  Jev 요청 종류나 OpenAI 요리 이름을 바꾼 까닭 (JevDecision.Reason)
     * @param broadAi     넓은 요청 요리 이름 AI를 불렀는지 (캐시 미스)
     */
    record Info(JevDecision.Decision decision, JevIntentJudgement judgement, RequestKind usedKind, List<String> usedValues,
                JevDecision.Reason usedReason, boolean broadAi, long broadMs, long broadInput, long broadOutput) {

        static final List<String> HEADER = Stream.of(
                Stream.of("decision", "jevIntent", "jevIntentConfidence", "jevFollowsPrevious", "jevRequestKind", "usedKind",
                        "broadDishes", "kindMatch", "broadAi", "broadMs", "broadInputTokens", "broadOutputTokens", "jevInputTokens", "jevMs",
                        "jevCategory", "jevCategoryConfidence", "jevCategory2", "jevCategory2Confidence", "usedReason", "usedValues"),
                // Jev 1차 의도별 확률 (ChatIntent 순서)
                Arrays.stream(ChatIntent.values()).map(intent -> PROBABILITY_PREFIX + intent.name()),
                // Jev 2차 맛 답 (Member.Taste 순서, "WANTED 0.91"처럼 답과 confidence)
                Arrays.stream(Member.Taste.values()).map(taste -> TASTE_PREFIX + taste.name())
        ).flatMap(column -> column).toList();

        // 자해 확률 (Jev 판단이 없으면 null)
        Double selfHarmProbability() {
            return judgement == null || judgement.first().intentProbabilities() == null ? null
                    : judgement.first().intentProbabilities().get(ChatIntent.SELF_HARM);
        }

        private JevIntentJudgement.Second second() {
            return judgement == null || !judgement.hasSecond() ? null : judgement.second();
        }

        long jevInputTokens() {
            if (judgement == null) {
                return 0;
            }
            long first = judgement.first().call().inputTokens() == null ? 0 : judgement.first().call().inputTokens();
            long second = judgement.hasSecond() && judgement.second().call().inputTokens() != null
                    ? judgement.second().call().inputTokens() : 0;
            return first + second;
        }

        // 요청 종류 일치. 기대 kind가 없거나 Jev로 정하지 않았으면 null (세지 않는다)
        Boolean kindMatch(Map<String, Object> expect) {
            Object expected = expect.get("kind");
            if (expected == null || !usesJevKind(decision) || usedKind == null) {
                return null;
            }
            return usedKind.name().equals(String.valueOf(expected));
        }

        List<String> cells(Map<String, Object> expect) {
            List<String> cells = new ArrayList<>(baseCells(expect));
            Map<ChatIntent, Double> probabilities = judgement == null ? null : judgement.first().intentProbabilities();
            for (ChatIntent intent : ChatIntent.values()) {
                Double probability = probabilities == null ? null : probabilities.get(intent);
                cells.add(probability == null ? "" : String.valueOf(probability));
            }
            for (Member.Taste taste : Member.Taste.values()) {
                JevIntentJudgement.TasteAnswer answer = second() == null ? null : second().tastes().get(taste);
                cells.add(answer == null ? "" : ChatIntentEvalManualTest.csv(answer.stance() + " " + answer.confidence()));
            }
            return cells;
        }

        private List<String> baseCells(Map<String, Object> expect) {
            return List.of(ChatIntentEvalManualTest.csv(decision),
                    ChatIntentEvalManualTest.csv(judgement == null ? null : judgement.first().answeredIntent()),
                    judgement == null ? "" : String.valueOf(judgement.first().intentConfidence()),
                    ChatIntentEvalManualTest.csv(judgement == null ? null : judgement.first().followsPrevious()),
                    ChatIntentEvalManualTest.csv(judgement == null || !judgement.hasSecond() ? null : judgement.second().requestKind()),
                    ChatIntentEvalManualTest.csv(usedKind),
                    ChatIntentEvalManualTest.csv(usedKind == RequestKind.BROAD ? usedValues : null),
                    ChatIntentEvalManualTest.csv(kindMatch(expect)),
                    String.valueOf(broadAi), String.valueOf(broadMs), String.valueOf(broadInput), String.valueOf(broadOutput),
                    String.valueOf(jevInputTokens()),
                    judgement == null ? "" : String.valueOf(judgement.totalLatencyMs()),
                    ChatIntentEvalManualTest.csv(second() == null ? null : second().category()),
                    second() == null ? "" : String.valueOf(second().categoryConfidence()),
                    ChatIntentEvalManualTest.csv(second() == null ? null : second().secondCategory()),
                    ChatIntentEvalManualTest.csv(second() == null ? null : second().secondCategoryConfidence()),
                    ChatIntentEvalManualTest.csv(usedReason),
                    ChatIntentEvalManualTest.csv(usedValues));
        }
    }

    /**
     * 비교: 기대 kind가 BROAD면 분류·키워드 기대값 대신 "Jev로 정했고 쓴 종류 BROAD, 요리 이름 있음"으로 본다.
     */
    static List<String> mismatches(Expect expect, Outcome outcome, EvalSet set, Info info) {
        if (!"BROAD".equals(String.valueOf(expect.raw().get("kind")))) {
            return expect.mismatches(outcome, set);
        }
        Map<String, Object> raw = new LinkedHashMap<>(expect.raw());
        BROAD_REPLACED.forEach(raw::remove);
        List<String> result = new ArrayList<>(new Expect(raw).mismatches(outcome, set));
        boolean broad = usesJevKind(info.decision()) && info.usedKind() == RequestKind.BROAD
                && !info.usedValues().isEmpty();
        if (!broad) {
            result.add("broad: 기대 JEV·BROAD·요리 이름, 실제 " + info.decision() + "·" + info.usedKind() + "·" + info.usedValues());
        }
        return result;
    }

    // Jev 요청 종류로 검색 조건을 정한 결정 (Jev로 정함)
    static boolean usesJevKind(JevDecision.Decision decision) {
        return decision == JevDecision.Decision.JEV;
    }

    // 정한 방법과 Jev 판단을 남기는 JevIntentRunner. 기다리기·결정 로그는 실제 동작 그대로
    static final class RecordingJevRunner extends JevIntentRunner {

        private JevIntentJudgement judgement;
        private JevDecision decision;

        RecordingJevRunner(JevIntentClassifier classifier, TypeSafeClient client, TypeSafeExecutor executor) {
            super(classifier, client, executor);
        }

        @Override
        public Optional<JevIntentJudgement> await(Run run) {
            Optional<JevIntentJudgement> judged = super.await(run);
            judgement = judged.orElse(null);
            return judged;
        }

        @Override
        public void logWhenDone(Run run, Long memberId, Long sessionId, int messageLength, ChatIntentResolution resolution,
                                    boolean mealkit, JevDecision decision) {
            this.decision = decision;
            super.logWhenDone(run, memberId, sessionId, messageLength, resolution, mealkit, decision);
        }
    }

    // 넓은 요청 요리 이름 AI 호출 여부·시간·토큰을 남긴다
    static final class RecordingDishGenerator implements SimilarDishGenerator {

        private final SimilarDishGenerator delegate;
        private final UsageRecorder usage;
        private boolean called;
        private long ms;
        private long input;
        private long output;

        RecordingDishGenerator(SimilarDishGenerator delegate, UsageRecorder usage) {
            this.delegate = delegate;
            this.usage = usage;
        }

        @Override
        public SimilarDishes generate(Long memberId, DishListKind kind, String text) {
            long startedAt = System.nanoTime();
            usage.clear();
            try {
                return delegate.generate(memberId, kind, text);
            } finally {
                called = true;
                ms = (System.nanoTime() - startedAt) / 1_000_000;
                input = usage.input();
                output = usage.output();
            }
        }
    }

    /**
     * 요약에 더할 Jev 줄: 정한 방법 분포와 정답률, 요청 종류 일치, 넓은 요청 요리 이름, 지연, 비용.
     */
    static List<String> summary(List<ChatIntentEvalManualTest.EvalCase> cases, List<ChatIntentEvalManualTest.RunRow> rows,
                                boolean safetySet) {
        List<String> lines = new ArrayList<>();
        List<ChatIntentEvalManualTest.RunRow> judged = rows.stream().filter(row -> row.jev() != null).toList();
        lines.add("");
        lines.add("[Jev] 의도는 confidence와 상관없이 Jev 1등 의도");
        Map<String, int[]> byDecision = new LinkedHashMap<>();
        for (JevDecision.Decision decision : JevDecision.Decision.values()) {
            byDecision.put(decision.name(), new int[2]);
        }
        for (ChatIntentEvalManualTest.RunRow row : judged) {
            int[] counts = byDecision.computeIfAbsent(String.valueOf(row.jev().decision()), name -> new int[2]);
            counts[0]++;
            counts[1] += row.pass() ? 1 : 0;
        }
        lines.add(String.format("정한 방법 (Jev를 부른 회차 %d, 단어 차단 제외): 회차 수 · 비율 · 그중 기대와 일치", judged.size()));
        byDecision.forEach((decision, counts) -> lines.add(String.format("  %-22s %4d · %5.1f%% · 일치 %d (%.1f%%)", decision,
                counts[0], percent(counts[0], judged.size()), counts[1], percent(counts[1], counts[0]))));
        List<Boolean> kindMatches = judged.stream().map(row -> row.jev().kindMatch(row.evalCase().expect().raw()))
                .filter(match -> match != null).toList();
        lines.add(String.format("요청 종류 일치 (Jev로 정한 회차 중 기대 kind가 있는 것, 일치 판단에는 넣지 않음): %d / %d",
                kindMatches.stream().filter(Boolean::booleanValue).count(), kindMatches.size()));

        lines.add("넓은 요청 요리 이름 (기대 kind BROAD이거나 Jev가 BROAD로 쓴 문장, 회차별 정한 방법·쓴 종류·요리 이름):");
        for (ChatIntentEvalManualTest.EvalCase evalCase : cases) {
            List<ChatIntentEvalManualTest.RunRow> caseRows = judged.stream().filter(row -> row.evalCase() == evalCase).toList();
            boolean broad = "BROAD".equals(String.valueOf(evalCase.expect().raw().get("kind")))
                    || caseRows.stream().anyMatch(row -> row.jev().usedKind() == RequestKind.BROAD);
            if (!broad || caseRows.isEmpty()) {
                continue;
            }
            List<String> runs = caseRows.stream().map(row -> row.jev().decision() + "·" + row.jev().usedKind()
                    + (row.jev().usedKind() == RequestKind.BROAD ? row.jev().usedValues().toString() : "")).toList();
            lines.add(String.format("  %s %s (기대 %s) → %s", evalCase.id(), evalCase.message(), evalCase.expect().raw().get("kind"),
                    String.join(" / ", runs)));
        }

        List<ChatIntentEvalManualTest.RunRow> resolved = rows.stream().filter(row -> row.source().equals("AI")).toList();
        lines.add("의도 결정까지 시간 ms (중앙값 / 95% / 최대): 전체 " + percentiles(resolved.stream().map(ChatIntentEvalManualTest.RunRow::ms).toList())
                + " · 넓은 요청 AI 호출(캐시 미스) " + percentiles(resolved.stream().filter(row -> row.jev() != null && row.jev().broadAi())
                .map(ChatIntentEvalManualTest.RunRow::ms).toList())
                + " · 그 밖 " + percentiles(resolved.stream().filter(row -> row.jev() == null || !row.jev().broadAi())
                .map(ChatIntentEvalManualTest.RunRow::ms).toList()));
        long intentInput = rows.stream().mapToLong(ChatIntentEvalManualTest.RunRow::inputTokens).sum();
        long intentOutput = rows.stream().mapToLong(ChatIntentEvalManualTest.RunRow::outputTokens).sum();
        long broadInput = judged.stream().mapToLong(row -> row.jev().broadInput()).sum();
        long broadOutput = judged.stream().mapToLong(row -> row.jev().broadOutput()).sum();
        long broadCalls = judged.stream().filter(row -> row.jev().broadAi()).count();
        long jevInput = judged.stream().mapToLong(row -> row.jev().jevInputTokens()).sum();
        lines.add(String.format("비용 (전체 회차 합, 캐시 할인 전): OpenAI 꺼내기 입력 %d·출력 %d토큰 $%.4f · 넓은 요청 AI %d번 입력 %d·출력 %d토큰 $%.4f"
                        + " · Jev 입력 %d토큰 $%.4f",
                intentInput, intentOutput, openAiCost(intentInput, intentOutput), broadCalls, broadInput, broadOutput,
                openAiCost(broadInput, broadOutput), jevInput, jevInput * JEV_USD_PER_MILLION_INPUT / 1_000_000));
        if (safetySet) {
            lines.addAll(selfHarmLines(cases, judged));
        }
        return lines;
    }

    /**
     * 자해 기준 세트 요약: 문장별 Jev 자해 확률(회차 최소~최대)과 1등 의도, 최종 의도. 자해 문장을 모두 잡고 아닌 문장은 잡지 않는 기준값 범위
     * (자해 문장 최소 확률 이하이고 아닌 문장 최대 확률 초과, 1등 의도가 자해면 확률과 상관없이 잡는다).
     */
    private static List<String> selfHarmLines(List<ChatIntentEvalManualTest.EvalCase> cases, List<ChatIntentEvalManualTest.RunRow> judged) {
        List<String> lines = new ArrayList<>();
        lines.add("[자해 기준] 지금 기준 SELF_HARM_MIN_PROBABILITY " + ChatIntentFacade.SELF_HARM_MIN_PROBABILITY
                + " (1등 의도가 자해이거나 자해 확률이 기준 이상이면 자해 안내)");
        lines.add("  id 묶음 문장 → 자해 확률 최소~최대 · Jev 1등 의도(회차별) · 최종 의도(회차별)");
        double selfHarmMin = Double.MAX_VALUE;
        double otherMax = 0;
        for (ChatIntentEvalManualTest.EvalCase evalCase : cases) {
            List<ChatIntentEvalManualTest.RunRow> caseRows = judged.stream().filter(row -> row.evalCase() == evalCase).toList();
            List<Double> probabilities = caseRows.stream().map(row -> row.jev().selfHarmProbability()).filter(p -> p != null).toList();
            List<String> top = caseRows.stream().map(row -> row.jev().judgement() == null ? "-"
                    : String.valueOf(row.jev().judgement().first().intent())).toList();
            List<String> finals = caseRows.stream().map(row -> String.valueOf(row.outcome().intent())).toList();
            double min = probabilities.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            double max = probabilities.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            boolean selfHarm = "SELF_HARM".equals(String.valueOf(evalCase.expect().raw().get("intent")));
            // 1등 의도가 자해인 회차는 확률과 상관없이 잡히므로 기준값 범위에서 뺀다
            for (ChatIntentEvalManualTest.RunRow row : caseRows) {
                Double probability = row.jev().selfHarmProbability();
                boolean topSelfHarm = row.jev().judgement() != null && row.jev().judgement().first().intent() == ChatIntent.SELF_HARM;
                if (probability == null || topSelfHarm) {
                    continue;
                }
                if (selfHarm) {
                    selfHarmMin = Math.min(selfHarmMin, probability);
                } else {
                    otherMax = Math.max(otherMax, probability);
                }
            }
            lines.add(String.format("  %s %s %s → %.3f~%.3f · %s · %s", evalCase.id(), evalCase.group(), evalCase.message(), min, max,
                    String.join("/", top), String.join("/", finals)));
        }
        lines.add(String.format("  기준값 범위 (1등 의도가 자해가 아닌 회차만): 아닌 문장 최대 %.3f 초과 ~ 자해 문장 최소 %s 이하",
                otherMax, selfHarmMin == Double.MAX_VALUE ? "(모두 1등 의도로 잡힘)" : String.format("%.3f", selfHarmMin)));
        return lines;
    }

    // gpt-4o-mini 단가 (USD / 1M 토큰, UsageRecorder와 같다)
    private static double openAiCost(long input, long output) {
        return (input * 0.15 + output * 0.60) / 1_000_000;
    }

    private static String percentiles(List<Long> values) {
        if (values.isEmpty()) {
            return "-";
        }
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((sorted.size() - 1) / 2) + " / " + sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)
                + " / " + sorted.get(sorted.size() - 1);
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    // 운영 Redis 대신 메모리 캐시 (키는 SimilarDishStore와 같게 종류 접두어 + 공백 제거·소문자, 빈 목록은 두지 않는다)
    static final class MemoryDishStore extends SimilarDishStore {

        private final Map<String, SimilarDishes> cache = new HashMap<>();

        MemoryDishStore() {
            super(null, null);
        }

        @Override
        public Optional<SimilarDishes> find(Long memberId, DishListKind kind, String dishText) {
            return Optional.ofNullable(cache.get(key(kind, dishText)));
        }

        @Override
        public void save(Long memberId, DishListKind kind, String dishText, SimilarDishes dishes) {
            if (dishes != null && !dishes.isEmpty()) {
                cache.put(key(kind, dishText), dishes);
            }
        }

        private static String key(DishListKind kind, String text) {
            return kind.keyPrefix() + ChatTexts.removeWhitespace(text).toLowerCase(Locale.ROOT);
        }
    }
}

package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.IntentResults;
import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalCase;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalSet;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.Outcome;
import org.example.takecareofmyfridgebe.domain.chat.intent.IntentCorrector;
import org.example.takecareofmyfridgebe.domain.chat.intent.JevIntentResolver;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyExclusionFinder;
import org.example.takecareofmyfridgebe.domain.chat.ai.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentRunner;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 꺼내기·서비스 규칙 기록 재생 테스트. 실제 AI가 낸 원래 결과(평가 before 실행, chat-intent-replay/inputs.csv)를 AI 대신 넣고
 * 서비스 규칙(ChatIntentFacade·JevIntentResolver·IntentCorrector)을 거친 최종 결과가 기준값(chat-intent-replay/expected.csv)과 같은지 본다.
 * AI를 부르지 않아 결정적이다. 의도는 OpenAI가 정하지 않으므로(Jev 단독) 흉내 낸 Jev가 평가 세트의 기대 의도(목록이면 첫째)와
 * 기대 이어짐을 답한다. 그래서 이 테스트는 의도 판단이 아니라 꺼낸 값과 서비스 규칙을 지킨다.
 * Jev의 맛·요청 종류·1등 분류는 기록한 AI 값으로 흉내 낸다 (ChatIntentFixture.judgementOf: 요리 이름이 있으면 특정 요리, 분류가 있으면 특정 분류).
 * 기준값을 다시 기록한 기록과 달라진 줄: cleanup-replay(보정 단계 삭제), d2-replay(REFINE → 추천 + 이어짐),
 * jev-only-replay(OpenAI 의도 판단 삭제) (docs/testing/eval-results 아래 replay-diff.csv)
 * <p>
 * 보정 코드를 옮기기만 할 때 결과가 그대로인지 확인하는 안전망이다. 동작을 일부러 바꾸는 커밋에서는 기준값을 다시 기록하고
 * 바뀐 줄을 그 커밋에서 확인한다. 기록하는 법 (take-care-of-my-fridge-be 폴더에서):
 * <pre>
 * CHAT_INTENT_REPLAY_RECORD=true ./gradlew test --tests '*ChatIntentReplayTest'
 * </pre>
 * DB·ES가 필요한 부분(재료 사전, 레시피 이름 찾기, 분류 목록, 맥락)은 평가 세트(chat-intent-eval.yml) 사전을 평가와 똑같이 쓴다.
 * 집합인 칸은 정렬해서 적는다 (실행마다 순서가 달라지지 않게).
 */
@SpringBootTest(classes = ChatIntentReplayTest.class)
@EnableConfigurationProperties({ChatWordProperties.class, ChatCandidateProperties.class})
class ChatIntentReplayTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final String INPUTS = "chat-intent-replay/inputs.csv";
    private static final Path EXPECTED = Path.of("src", "test", "resources", "chat-intent-replay", "expected.csv");
    private static final Path ACTUAL = Path.of("build", "reports", "chat-intent-replay", "actual.csv");
    private static final String LIST_DELIMITER = " / ";

    @Autowired
    private ChatWordProperties words;
    @Autowired
    private ChatCandidateProperties candidateProperties;

    @Test
    @DisplayName("기록한 AI 원래 결과를 서비스 규칙에 다시 넣으면 최종 결과가 기준값과 같다")
    void replaysRecordedAiResults() throws IOException {
        EvalSet set = EvalSet.load();
        Map<String, EvalCase> cases = set.cases(Set.of()).stream()
                .collect(Collectors.toMap(EvalCase::id, evalCase -> evalCase));
        ReplayExtractor extractor = new ReplayExtractor();
        JevIntentRunner jev = mock(JevIntentRunner.class);
        JevIntentRunner.Run run = mock(JevIntentRunner.Run.class);
        when(jev.start(any(), any(), any())).thenReturn(run);
        ChatIntentFacade facade = facade(set, extractor, jev);

        List<String> actual = new ArrayList<>();
        actual.add("id,run,intent,wanted,avoid,categories,keywords,dish,preference,maxMinutes,include,exclude,"
                + "useSelectedOnly,scope,names,saleOnly,productType,target,question");
        for (Map<String, String> row : readInputs()) {
            EvalCase evalCase = cases.get(row.get("id"));
            IntentResult recorded = withExpectedIntent(rawOf(row), evalCase == null ? Map.of() : evalCase.expect().raw());
            extractor.next = ChatIntentFixture.extractionOf(recorded);
            when(jev.await(run)).thenReturn(Optional.of(ChatIntentFixture.judgementOf(recorded)));
            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of(row.get("message")), set.context(blankToNull(row.get("context"))));
            actual.add(row.get("id") + "," + row.get("run") + "," + line(Outcome.of(resolution, set)));
        }

        // 기준값과 비교하기 쉽게 이번 결과를 build 아래에도 남긴다
        Files.createDirectories(ACTUAL.getParent());
        Files.writeString(ACTUAL, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
        if ("true".equals(System.getenv("CHAT_INTENT_REPLAY_RECORD"))) {
            // 운영체제와 상관없이 같은 파일이 되게 줄바꿈은 LF로 쓴다
            Files.writeString(EXPECTED, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
            return;
        }
        List<String> expected = Files.readAllLines(EXPECTED, StandardCharsets.UTF_8);
        List<String> diffs = new ArrayList<>();
        for (int i = 0; i < Math.max(expected.size(), actual.size()); i++) {
            String want = i < expected.size() ? expected.get(i) : "(없음)";
            String got = i < actual.size() ? actual.get(i) : "(없음)";
            if (!want.equals(got)) {
                diffs.add("기대 " + want + "\n실제 " + got);
            }
        }
        assertThat(diffs).as("기준값과 다른 줄 %d개", diffs.size()).isEmpty();
    }

    // 평가(ChatIntentEvalManualTest.facade)와 같은 사전·목으로 만든다
    private ChatIntentFacade facade(EvalSet set, ChatIntentExtractor extractor, JevIntentRunner jev) {
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
        when(allergyExclusionFinder.find(any(), any())).thenReturn(AllergyExclusionFinder.Allergens.NONE);
        IntentCorrector corrector = new IntentCorrector(recipeService, ingredientMatcher, mock(ChatMessageService.class), words);
        // 기록에는 Jev 판단이 없어 흉내 낸 Jev(평가 세트 기대 의도)로 정한다. 넓은 요청 요리 이름은 부르지 않는다(흉내 낸 Jev는 넓은 요청을 답하지 않는다)
        return new ChatIntentFacade(extractor, recipeService, ingredientMatcher, candidateProperties, words,
                allergyExclusionFinder, corrector, jev, new JevIntentResolver(mock(SimilarDishFinder.class), words));
    }

    // 기록한 칸으로 AI 원래 결과를 다시 만든다 (평가 CSV의 AI 칸과 같은 모양, 목록은 " / "로 이어 씀).
    // 의도 칸의 +FOLLOWS(예전 기록은 REFINE)는 추천 + 이어짐 예다
    private static IntentResult rawOf(Map<String, String> row) {
        String intent = blankToNull(row.get("aiIntent"));
        return IntentResults.of(
                intent == null ? null : ChatIntentEvalManualTest.intentOf(intent),
                list(row.get("aiWanted")),
                list(row.get("aiCategories")),
                integer(row.get("aiMaxMinutes")),
                list(row.get("aiInclude")),
                list(row.get("aiExclude")),
                blankToNull(row.get("aiUseSelectedOnly")) == null ? null : Boolean.valueOf(row.get("aiUseSelectedOnly")),
                list(row.get("aiKeywords")),
                blankToNull(row.get("aiDish")),
                blankToNull(row.get("aiPreference")),
                integer(row.get("aiTargetIndex")),
                blankToNull(row.get("aiTargetName")),
                list(row.get("aiRemoved")),
                list(row.get("aiAvoid")),
                blankToNull(row.get("aiQuestion")),
                blankToNull(row.get("aiScope")),
                intent != null && ChatIntentEvalManualTest.followsOf(intent));
    }

    // 기록한 AI 결과의 의도·이어짐을 평가 세트의 기대 의도(목록이면 첫째)·기대 이어짐으로 바꾼다.
    // 기대 의도가 없는 문장(T09)과 평가 세트에서 빠진 문장(X01~X05, 모두 추천으로 기록)은 기록한 의도를 둔다
    private static IntentResult withExpectedIntent(IntentResult recorded, Map<String, Object> expect) {
        Object intent = expect.get("intent");
        ChatIntent expected = intent == null ? recorded.intent()
                : ChatIntent.valueOf(intent instanceof List<?> names ? String.valueOf(names.get(0)) : String.valueOf(intent));
        boolean follows = Boolean.TRUE.equals(expect.get("follows"));
        return IntentResults.of(expected, recorded.wantedTastes(), recorded.moodCategories(), recorded.maxMinutes(),
                recorded.includeIngredients(), recorded.excludeIngredients(), recorded.useSelectedOnly(), recorded.keywords(),
                recorded.dishText(), recorded.preferenceText(), recorded.targetIndex(), recorded.targetName(), recorded.removed(),
                recorded.avoidTastes(), recorded.detailQuestion(), recorded.fridgeScope(), follows);
    }

    private static List<Map<String, String>> readInputs() throws IOException {
        List<String> lines = new ClassPathResource(INPUTS).getContentAsString(StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> header = cells(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = cells(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < cells.size() ? cells.get(i) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    // 큰따옴표로 감싼 칸("" 는 따옴표 하나)을 지원하는 CSV 한 줄 나누기
    static List<String> cells(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    // 집합 칸은 정렬해서, 목록 칸은 순서대로 적는다
    private static String line(Outcome outcome) {
        return String.join(",", List.of(
                cell(ChatIntentEvalManualTest.intentLabel(outcome.intent(), outcome.follows())), sorted(outcome.wanted()), sorted(outcome.avoid()), sorted(outcome.categories()),
                cell(String.join(LIST_DELIMITER, outcome.keywords())), cell(outcome.dish()), cell(outcome.preference()),
                cell(outcome.maxMinutes()), sorted(outcome.include()), sorted(outcome.exclude()),
                cell(outcome.useSelectedOnly()), cell(outcome.scope()), sorted(outcome.names()), cell(outcome.saleOnly()),
                cell(outcome.productType()), cell(outcome.target()), cell(outcome.question())));
    }

    private static String sorted(Collection<String> values) {
        return cell(String.join(LIST_DELIMITER, new TreeSet<>(values)));
    }

    private static String cell(Object value) {
        return value == null ? "" : "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private static List<String> list(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(" / "));
    }

    private static Integer integer(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value.strip());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // 다음 문장에 돌려줄 OpenAI 꺼내기 원래 결과를 미리 정해 둔다
    private static final class ReplayExtractor implements ChatIntentExtractor {

        private IntentExtraction next;

        @Override
        public IntentExtraction extract(Long memberId, Long sessionId, String message, String followUp) {
            return next;
        }
    }
}

package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAskWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCategoryWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatFoodWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatFridgeShopWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatMealkitWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteOppositeProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalSet;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.Outcome;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatIntentExtractor;
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
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 의도 보정 기록 재생 테스트. 실제 AI가 낸 원래 결과(평가 before 실행, chat-intent-replay/inputs.csv)를 AI 대신 넣고
 * 서버 보정(ChatIntentFacade)을 거친 최종 결과가 기준값(chat-intent-replay/expected.csv)과 같은지 본다. AI를 부르지 않아 결정적이다.
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
@EnableConfigurationProperties({ChatTasteWordProperties.class, ChatMealkitWordProperties.class, ChatAskWordProperties.class,
        ChatCategoryWordProperties.class, ChatFoodWordProperties.class, ChatTasteOppositeProperties.class,
        ChatFridgeShopWordProperties.class, ChatCandidateProperties.class})
class ChatIntentReplayTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final String INPUTS = "chat-intent-replay/inputs.csv";
    private static final Path EXPECTED = Path.of("src", "test", "resources", "chat-intent-replay", "expected.csv");
    private static final String LIST_DELIMITER = " / ";

    @Autowired
    private ChatTasteWordProperties tasteWords;
    @Autowired
    private ChatMealkitWordProperties mealkitWords;
    @Autowired
    private ChatAskWordProperties askWords;
    @Autowired
    private ChatCategoryWordProperties categoryWords;
    @Autowired
    private ChatFoodWordProperties foodWords;
    @Autowired
    private ChatTasteOppositeProperties tasteOpposites;
    @Autowired
    private ChatFridgeShopWordProperties fridgeShopWords;
    @Autowired
    private ChatCandidateProperties candidateProperties;

    @Test
    @DisplayName("기록한 AI 원래 결과를 서버 보정에 다시 넣으면 최종 결과가 기준값과 같다")
    void replaysRecordedAiResults() throws IOException {
        EvalSet set = EvalSet.load();
        ReplayExtractor extractor = new ReplayExtractor();
        ChatIntentFacade facade = facade(set, extractor);

        List<String> actual = new ArrayList<>();
        actual.add("id,run,intent,wanted,avoid,categories,keywords,dish,preference,maxMinutes,include,exclude,"
                + "useSelectedOnly,scope,names,saleOnly,productType,target,question");
        for (Map<String, String> row : readInputs()) {
            extractor.next = rawOf(row);
            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of(row.get("message")), set.context(blankToNull(row.get("context"))));
            actual.add(row.get("id") + "," + row.get("run") + "," + line(Outcome.of(resolution, set)));
        }

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
    private ChatIntentFacade facade(EvalSet set, ChatIntentExtractor extractor) {
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
        return new ChatIntentFacade(extractor, recipeService, ingredientMatcher, candidateProperties,
                mock(ChatMessageService.class), tasteWords, mealkitWords, askWords, categoryWords, foodWords,
                allergyExclusionFinder, tasteOpposites, fridgeShopWords, EvalCorrectionSwitches.properties(Set.of()));
    }

    // 기록한 칸으로 AI 원래 결과를 다시 만든다 (평가 CSV의 AI 칸과 같은 모양, 목록은 " / "로 이어 씀)
    private static IntentResult rawOf(Map<String, String> row) {
        String intent = blankToNull(row.get("aiIntent"));
        return new IntentResult(
                intent == null ? null : ChatIntent.valueOf(intent),
                list(row.get("aiWanted")),
                list(row.get("aiCategories")),
                integer(row.get("aiMaxMinutes")),
                list(row.get("aiInclude")),
                list(row.get("aiExclude")),
                blankToNull(row.get("aiUseSelectedOnly")) == null ? null : Boolean.valueOf(row.get("aiUseSelectedOnly")),
                list(row.get("aiKeywords")),
                null,
                blankToNull(row.get("aiPreference")),
                integer(row.get("aiTargetIndex")),
                blankToNull(row.get("aiTargetName")),
                list(row.get("aiRemoved")),
                list(row.get("aiAvoid")),
                blankToNull(row.get("aiQuestion")),
                blankToNull(row.get("aiScope")));
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
                cell(outcome.intent()), sorted(outcome.wanted()), sorted(outcome.avoid()), sorted(outcome.categories()),
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

    // 다음 문장에 돌려줄 AI 원래 결과를 미리 정해 둔다
    private static final class ReplayExtractor implements ChatIntentExtractor {

        private IntentResult next;

        @Override
        public IntentResult extract(Long memberId, Long sessionId, String message, List<String> categories, String followUp) {
            return next;
        }
    }
}

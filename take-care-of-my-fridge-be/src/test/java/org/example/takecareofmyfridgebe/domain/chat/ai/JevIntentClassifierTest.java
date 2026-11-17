package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// TypeSafe API를 흉내 내 Jev 1차·2차 요청 본문, 답 읽기, 2차를 부르는 조건, 실패 처리를 확인한다.
// 응답 값은 docs/testing/jev-api-notes.md의 실제 응답(a·c·d)을 질문 이름만 바꿔 옮겼다
class JevIntentClassifierTest {

    private static final String BASE_URL = "https://api.typesafe.test";
    private static final String URL = BASE_URL + "/v1/systemone";
    // 테스트용 가짜 키 (실제 키가 아니다)
    private static final String FAKE_KEY = "fake-test-key-0000";
    private static final String MODEL = "jev-1.14.0";
    private static final String MESSAGE = "매운 찌개 추천해줘";
    private static final String REQUEST_ID = "x-typesafe-request-id";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("직전 추천이 있고 추천인데 이어짐 확률이 기준 0.45 이상이면 다시 추천(REFINE), 미만이면 새 추천이다")
    void returnsRefine_whenFollowsPreviousAtThreshold() {
        assertThat(JevIntentClassifier.MIN_FOLLOWS_PREVIOUS).isEqualTo(0.45);
        assertThat(JevIntentClassifier.serviceIntent(ChatIntent.RECOMMEND, true, 0.45)).isEqualTo(ChatIntent.REFINE);
        assertThat(JevIntentClassifier.serviceIntent(ChatIntent.RECOMMEND, true, 0.44)).isEqualTo(ChatIntent.RECOMMEND);
    }

    private static final List<String> CATEGORIES = List.of(
            "과자", "구이", "국", "그라탕", "기타", "김치", "디저트", "떡", "만두", "말이", "면류", "무침", "밥", "볶음", "빵", "샌드위치",
            "샐러드", "수프", "스튜", "쌈", "양념", "음료", "전", "전골", "절임", "조림", "죽", "찌개", "찜", "튀김", "피자", "한과", "회");
    // jev-api-notes.md b (state "따뜻한 국물 요리 추천해줘")의 분류 Choice 답
    private static final String CATEGORY_GUK = "\"category\":{\"type\":\"choice\",\"choice\":\"국\",\"confidence\":0.71,"
            + "\"probabilities\":{\"국\":0.72,\"없음\":0.22,\"수프\":0.04,\"찌개\":0.01,\"기타\":0.01}}";
    private static final String CATEGORY_NONE = "\"category\":{\"type\":\"choice\",\"choice\":\"없음\",\"confidence\":0.9,"
            + "\"probabilities\":{\"없음\":0.93,\"국\":0.05,\"찌개\":0.02}}";

    private static final String FIRST_RECOMMEND = firstResponse(
            "{\"type\":\"choice\",\"choice\":\"추천\",\"confidence\":1.0,\"probabilities\":"
                    + "{\"추천\":1.0,\"일반 질문\":0.0,\"냉장고 보기\":0.0,\"장보기\":0.0,\"상관없는 말\":0.0}}");
    // 직전 추천이 있을 때 추천 0.9 (이어짐 확률은 테스트마다 붙인다)
    private static final String RECOMMEND_ANSWER = "{\"type\":\"choice\",\"choice\":\"추천\",\"confidence\":0.9,\"probabilities\":"
            + "{\"추천\":0.92,\"레시피 자세히\":0.06,\"일반 질문\":0.02}}";
    private static final String FIRST_GENERAL = firstResponse(
            "{\"type\":\"choice\",\"choice\":\"일반 질문\",\"confidence\":0.95,\"probabilities\":"
                    + "{\"추천\":0.03,\"일반 질문\":0.96,\"냉장고 보기\":0.01}}");
    private static final String FIRST_RECIPE_DETAIL_ANSWER =
            "{\"type\":\"choice\",\"choice\":\"레시피 자세히\",\"confidence\":0.83,\"probabilities\":"
                    + "{\"추천\":0.12,\"레시피 자세히\":0.86,\"일반 질문\":0.02}}";
    private static final String FIRST_RECIPE_DETAIL = firstResponse(FIRST_RECIPE_DETAIL_ANSWER);
    private static final String SECOND = secondResponse(CATEGORY_GUK);

    private final List<byte[]> sentBodies = new ArrayList<>();
    private MockRestServiceServer server;

    @Test
    @DisplayName("직전 추천이 없으면 1차 요청은 UTF-8 한글 state와 의도·묻는 말·냉장고 범위 질문 3개를 보내고, 의도 선택지에 이어서 다시·레시피 자세히가 없다")
    void sendsFirstQuestionsInUtf8_whenClassify() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_GENERAL, "req_first");

        classifier.classify(MESSAGE, CATEGORIES, null);

        server.verify();
        byte[] sent = sentBodies.get(0);
        String utf8 = new String(sent, StandardCharsets.UTF_8);
        assertThat(utf8).contains(MESSAGE);
        Map<String, Object> body = parse(sent);
        assertThat(body.get("model")).isEqualTo(MODEL);
        assertThat(body.get("state")).isEqualTo(MESSAGE);
        Map<String, Object> questions = map(body.get("questions"));
        assertThat(questions).containsOnlyKeys("intent", "asks_question", "fridge_scope");
        assertThat(map(questions.get("intent")).get("type")).isEqualTo("choice");
        // 이어짐은 추천 안의 항목이라 이어서 다시 선택지가 없고, 직전 추천이 없으면 레시피 자세히도 고를 수 없어 뺀다. 이어짐 질문도 없다
        assertThat(map(map(questions.get("intent")).get("criteria")).keySet()).containsExactly(
                "추천", "일반 질문", "냉장고 보기", "장보기", "상관없는 말", "막을 말", "자해");
        assertThat(map(map(questions.get("intent")).get("criteria"))).containsEntry("추천",
                "메뉴·레시피·요리를 정해 달라는 말. 새로 정해 달라는 말과, [직전 추천]에 이어서 다른 것을 달라거나 조건을 바꿔 달라는 말 모두 추천이다. "
                        + "\"뭐 먹지?\", \"~ 먹고 싶어\", 음식·재료·요리 종류·밀키트 이름만 말한 것도 추천이다. "
                        + "무엇을 빼거나 피해 달라는 조건이 붙어도 추천이다. 재료를 사고 싶다는 말은 추천이 아니라 장보기다");
        assertThat(map(questions.get("asks_question")))
                .containsEntry("type", "noul")
                .containsEntry("instructions", "정보를 묻는 말인가(보관법·손질법·뜻 등). 요리를 정해 달라는 말은 아니다")
                .doesNotContainKey("criteria");
        assertThat(map(map(questions.get("fridge_scope")).get("criteria")).keySet())
                .containsExactly("전체", "밀키트", "임박", "양념");
    }

    @Test
    @DisplayName("1차 의도가 추천이면 2차로 요리 이름·특징·재료·밀키트 Noul, 맛 Choice 5개, 요청 종류 Choice, 분류 Choice를 보낸다")
    void sendsSecondQuestions_whenIntentIsRecommend() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_RECOMMEND, "req_first");
        expectCall(SECOND, "req_second");

        classifier.classify(MESSAGE, CATEGORIES, null);

        server.verify();
        Map<String, Object> body = parse(sentBodies.get(1));
        assertThat(body.get("model")).isEqualTo(MODEL);
        assertThat(body.get("state")).isEqualTo(MESSAGE);
        Map<String, Object> questions = map(body.get("questions"));
        List<String> expectedNames = new ArrayList<>(List.of("dish_named", "feature_named", "ingredient_named", "mealkit_wanted",
                "taste_salty", "taste_sweet", "taste_spicy", "taste_umami", "taste_mild", "request_kind", "category"));
        assertThat(questions.keySet()).containsExactlyElementsOf(expectedNames);
        assertThat(map(questions.get("dish_named"))).containsEntry("type", "noul")
                .containsEntry("instructions", "특정 요리 이름을 말했나");
        Map<String, Object> spicy = map(questions.get("taste_spicy"));
        assertThat(spicy.get("type")).isEqualTo("choice");
        assertThat(spicy.get("instructions")).isEqualTo("사용자가 매운맛(얼큰하고 칼칼한 맛)을 원하나, 피하고 싶어 하나, 말하지 않았나?");
        assertThat(map(spicy.get("criteria"))).containsExactly(
                Map.entry("원함", "매운맛을 원한다"),
                Map.entry("피함", "매운맛을 피하고 싶다"),
                Map.entry("언급 없음", "매운맛에 대해 말하지 않았다. 다른 맛을 원하거나 피하는 말은 매운맛과 상관없다"));
        // 단어 규칙("말고", "안", "덜", "싫어")을 보내지 않는다
        assertThat(questions.values().stream().map(String::valueOf)).noneMatch(text -> text.contains("말고"));
        Map<String, Object> requestKind = map(questions.get("request_kind"));
        assertThat(requestKind).containsEntry("type", "choice").containsEntry("instructions", "사용자는 원하는 요리를 어떻게 말했나?");
        assertThat(map(requestKind.get("criteria"))).containsExactly(
                Map.entry("특정 요리", "요리 하나의 이름을 말했다"),
                Map.entry("특정 분류", "요리의 종류를 말했고, 그 종류가 분류 목록의 하나에 맞는다(찌개, 디저트처럼). 빼거나 피하는 말이 함께 있어도 이 기준은 같다"),
                Map.entry("넓은 요청", "상황이나 쓰임새, 여러 종류에 걸친 말을 했다(술안주, 반찬, 국물, 간단한 한 끼처럼). 말한 종류가 여러 분류에 걸치면 넓은 요청이다"),
                Map.entry("없음", "요리 종류나 이름을 말하지 않았다"));
        Map<String, Object> category = map(questions.get("category"));
        assertThat(category).containsEntry("type", "choice").containsEntry("instructions", "사용자가 원하는 요리는 어느 분류인가?");
        List<String> expectedOptions = new ArrayList<>(CATEGORIES);
        expectedOptions.add("없음");
        assertThat(map(category.get("criteria")).keySet()).containsExactlyElementsOf(expectedOptions);
        assertThat(map(category.get("criteria"))).containsEntry("국", "국 분류의 요리")
                .containsEntry("없음", "요리 종류를 말하지 않았거나 맞는 분류가 없다");
    }

    @Test
    @DisplayName("1차·2차 답을 읽어 의도·확률·맛·Noul·요청 종류·1등 분류를 담는다")
    void readsAnswers_whenBothCallsSucceed() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_RECOMMEND, "req_first");
        expectCall(SECOND, "req_second");

        JevIntentJudgement judgement = classifier.classify(MESSAGE, CATEGORIES, null).orElseThrow();

        JevIntentJudgement.First first = judgement.first();
        assertThat(first.intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(first.intentConfidence()).isEqualTo(1.0);
        // 이어서 다시는 의도 선택지가 아니라(이어짐은 따로 묻는다) 확률에 없다
        assertThat(first.intentProbabilities()).hasSize(8)
                .containsEntry(ChatIntent.RECOMMEND, 1.0)
                .doesNotContainKey(ChatIntent.REFINE);
        assertThat(first.followsPrevious()).isNull();
        assertThat(first.asksQuestion()).isEqualTo(0.09);
        assertThat(first.fridgeScope()).isEqualTo(FridgeScope.ALL);
        assertThat(first.fridgeScopeConfidence()).isEqualTo(0.9);
        assertThat(first.call()).isEqualTo(new JevIntentJudgement.Call(MODEL, "req_first", first.call().latencyMs(), 417));

        JevIntentJudgement.Second second = judgement.second();
        assertThat(second.dishNamed()).isEqualTo(0.09);
        assertThat(second.featureNamed()).isEqualTo(0.74);
        assertThat(second.ingredientNamed()).isEqualTo(0.07);
        assertThat(second.mealkitWanted()).isEqualTo(0.08);
        assertThat(second.tastes()).containsEntry(Member.Taste.SPICY, new TasteAnswer(TasteStance.NOT_MENTIONED, 0.98))
                .containsEntry(Member.Taste.UMAMI, new TasteAnswer(TasteStance.NOT_MENTIONED, 0.89))
                .hasSize(5);
        assertThat(second.requestKind()).isEqualTo(RequestKind.BROAD);
        assertThat(second.requestKindConfidence()).isEqualTo(0.8);
        assertThat(second.category()).isEqualTo("국");
        assertThat(second.categoryConfidence()).isEqualTo(0.71);
        assertThat(second.call().requestId()).isEqualTo("req_second");
        assertThat(second.call().inputTokens()).isEqualTo(1893);
        assertThat(judgement.totalLatencyMs()).isGreaterThanOrEqualTo(first.call().latencyMs() + second.call().latencyMs());
    }

    @Test
    @DisplayName("분류 Choice의 1등이 없음이면 1등 분류를 null로 담는다")
    void readsNullCategory_whenNoneIsTop() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_RECOMMEND, "req_first");
        expectCall(secondResponse(CATEGORY_NONE), "req_second");

        JevIntentJudgement.Second second = classifier.classify("뭐 먹지?", CATEGORIES, null).orElseThrow().second();

        assertThat(second.category()).isNull();
        assertThat(second.categoryConfidence()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("직전 추천이 있으면 이번 메시지를 붙인 state로 레시피 자세히가 있는 의도 선택지와 이어짐 질문을 보내고, 추천 + 이어짐 0.8이면 REFINE으로 넘기고 2차도 부른다")
    void passesRefineAndCallsSecond_whenRecommendFollowsPrevious() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(firstResponse(RECOMMEND_ANSWER, 0.8), "req_first");
        expectCall(SECOND, "req_second");
        String followUp = "[직전 추천] 1. 김치찌개 / 2. 순두부찌개\n[직전 조건] 분류 찌개";

        JevIntentJudgement judgement = classifier.classify("좀 더 매운 걸로", CATEGORIES, followUp).orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.REFINE);
        assertThat(judgement.first().answeredIntent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(judgement.first().intentConfidence()).isEqualTo(0.9);
        assertThat(judgement.first().followsPrevious()).isEqualTo(0.8);
        assertThat(judgement.hasSecond()).isTrue();
        String expectedState = "[직전 추천] 1. 김치찌개 / 2. 순두부찌개\n[직전 조건] 분류 찌개\n[이번 말] 좀 더 매운 걸로";
        assertThat(parse(sentBodies.get(0)).get("state")).isEqualTo(expectedState);
        assertThat(parse(sentBodies.get(1)).get("state")).isEqualTo(expectedState);
        Map<String, Object> questions = map(parse(sentBodies.get(0)).get("questions"));
        assertThat(questions).containsOnlyKeys("intent", "asks_question", "fridge_scope", "follows_previous");
        assertThat(map(map(questions.get("intent")).get("criteria")).keySet()).containsExactly(
                "추천", "일반 질문", "레시피 자세히", "냉장고 보기", "장보기", "상관없는 말", "막을 말", "자해");
        assertThat(map(questions.get("follows_previous")))
                .containsEntry("type", "noul")
                .containsEntry("instructions", "[직전 추천]의 조건(맛, 재료, 종류)을 이어받으면서 일부만 바꿔 달라는 말인가? "
                        + "직전 추천을 보고 조건을 더하거나 빼거나 바꿔 달라는 말이면 예다. "
                        + "직전 추천에 덧붙여 말했어도 직전 조건과 상관없이 새로 요리를 정해 달라는 말이면 아니오다");
    }

    @Test
    @DisplayName("직전 추천이 있어도 추천 + 이어짐 0.3이면 새 추천(RECOMMEND)으로 두고 2차를 부른다")
    void keepsRecommendAndCallsSecond_whenRecommendDoesNotFollowPrevious() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(firstResponse(RECOMMEND_ANSWER, 0.3), "req_first");
        expectCall(SECOND, "req_second");

        JevIntentJudgement judgement = classifier.classify("디저트도 추천해줘", CATEGORIES, "[직전 추천] 1. 김치찌개").orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(judgement.first().followsPrevious()).isEqualTo(0.3);
        assertThat(judgement.hasSecond()).isTrue();
    }

    @Test
    @DisplayName("1차 의도가 추천이 아니면 2차를 부르지 않는다")
    void skipsSecond_whenIntentIsGeneral() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_GENERAL, "req_first");

        JevIntentJudgement judgement = classifier.classify("두부 보관 어떻게 해?", CATEGORIES, null).orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.GENERAL);
        assertThat(judgement.hasSecond()).isFalse();
    }

    @Test
    @DisplayName("직전 추천이 없는데 (선택지에 없던) 레시피 자세히로 답해도 안전망으로 추천으로 보고 2차를 묻는다")
    void asksSecondAsRecommend_whenRecipeDetailWithoutFollowUp() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_RECIPE_DETAIL, "req_first");
        expectCall(SECOND, "req_second");

        JevIntentJudgement judgement = classifier.classify("매운 닭볶음탕 레시피", CATEGORIES, null).orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(judgement.first().answeredIntent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
        assertThat(judgement.hasSecond()).isTrue();
    }

    @Test
    @DisplayName("직전 추천이 있으면 레시피 자세히는 이어짐 확률과 상관없이 그대로 두고 2차를 묻지 않는다")
    void keepsRecipeDetail_whenFollowUpExists() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(firstResponse(FIRST_RECIPE_DETAIL_ANSWER, 0.9), "req_first");

        JevIntentJudgement judgement = classifier.classify("두 번째 거 어떻게 만들어?", CATEGORIES,
                "[직전 추천] 1. 김치찌개 / 2. 순두부찌개").orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
        assertThat(judgement.first().answeredIntent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
        assertThat(judgement.hasSecond()).isFalse();
    }

    @Test
    @DisplayName("2차 호출이 실패하면 예외 없이 1차 결과만 돌려준다")
    void returnsFirstOnly_whenSecondFails() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_RECOMMEND, "req_first");
        expect(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).header(REQUEST_ID, "req_second"));

        JevIntentJudgement judgement = classifier.classify(MESSAGE, CATEGORIES, null).orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(judgement.second()).isNull();
    }

    static Stream<Arguments> firstFailures() {
        return Stream.of(
                Arguments.of("타임아웃", (ResponseCreator) request -> {
                    throw new SocketTimeoutException("Read timed out");
                }),
                Arguments.of("401", withStatus(HttpStatus.UNAUTHORIZED).header(REQUEST_ID, "req_fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":{\"error_type\":\"authentication_error\",\"message\":\"Cannot authenticate\"}}")),
                Arguments.of("422", withStatus(HttpStatus.UNPROCESSABLE_CONTENT).header(REQUEST_ID, "req_fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":[{\"type\":\"too_short\",\"loc\":[\"body\",\"questions\"]}]}")),
                Arguments.of("429", withStatus(HttpStatus.TOO_MANY_REQUESTS).header(REQUEST_ID, "req_fail")),
                Arguments.of("JSON이 아닌 본문", withSuccess("not json", MediaType.APPLICATION_JSON)),
                Arguments.of("모르는 선택지", withSuccess(firstResponse(
                        "{\"type\":\"choice\",\"choice\":\"없는 의도\",\"confidence\":0.5,\"probabilities\":{}}"),
                        MediaType.APPLICATION_JSON)),
                Arguments.of("답 빠짐", withSuccess(
                        "{\"model\":\"jev-1.14.0\",\"answers\":{},\"usage\":{\"input_tokens\":1}}", MediaType.APPLICATION_JSON)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstFailures")
    @DisplayName("1차 호출이 타임아웃·HTTP 오류·응답 형식 오류면 예외 없이 빈 결과를 돌려주고 2차를 부르지 않는다")
    void returnsEmpty_whenFirstFails(String caseName, ResponseCreator response) {
        JevIntentClassifier classifier = classifier(withKey());
        expect(response);

        Optional<JevIntentJudgement> judgement = classifier.classify(MESSAGE, CATEGORIES, null);

        server.verify();
        assertThat(judgement).isEmpty();
    }

    @Test
    @DisplayName("키가 비어 있으면 호출하지 않고 빈 결과를 돌려준다")
    void returnsEmptyWithoutCall_whenApiKeyBlank() {
        JevIntentClassifier classifier = classifier(new TypeSafeProperties(BASE_URL, " ", MODEL, Duration.ofSeconds(3)));

        assertThat(classifier.classify(MESSAGE, CATEGORIES, null)).isEmpty();
        server.verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("호출이 실패하면 상태 코드와 request-id를 WARN 로그로 남기고 메시지 원문과 키는 쓰지 않는다")
    void logsStatusWithoutMessageAndKey_whenCallFails(CapturedOutput output) {
        JevIntentClassifier classifier = classifier(withKey());
        expect(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).header(REQUEST_ID, "req_fail")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":[{\"type\":\"too_short\",\"input\":\"" + MESSAGE + "\"}]}"));

        classifier.classify(MESSAGE, CATEGORIES, null);

        assertThat(output.getOut()).contains("WARN", "status: 422", "requestId: req_fail", "글 길이: " + MESSAGE.length())
                .doesNotContain(MESSAGE)
                .doesNotContain(FAKE_KEY);
    }

    private JevIntentClassifier classifier(TypeSafeProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        return new JevIntentClassifier(new TypeSafeClient(builder.build(), properties));
    }

    private static TypeSafeProperties withKey() {
        return new TypeSafeProperties(BASE_URL, FAKE_KEY, MODEL, Duration.ofSeconds(3));
    }

    private void expectCall(String responseBody, String requestId) {
        expect(withSuccess(responseBody, MediaType.APPLICATION_JSON).header(REQUEST_ID, requestId));
    }

    private void expect(ResponseCreator response) {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + FAKE_KEY))
                .andExpect(content().contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8)))
                .andExpect(captureBody())
                .andRespond(response);
    }

    private RequestMatcher captureBody() {
        return request -> sentBodies.add(((MockClientHttpRequest) request).getBodyAsBytes());
    }

    private static String firstResponse(String intentAnswer) {
        return firstResponse(intentAnswer, null);
    }

    // followsPrevious가 있으면 이어짐 Noul 답을 붙인다 (직전 추천이 있을 때)
    private static String firstResponse(String intentAnswer, Double followsPrevious) {
        return "{\"model\":\"jev-1.14.0\",\"answers\":{"
                + "\"intent\":" + intentAnswer + ","
                + (followsPrevious == null ? "" : "\"follows_previous\":{\"type\":\"noul\",\"noul\":" + followsPrevious + "},")
                + "\"asks_question\":{\"type\":\"noul\",\"noul\":0.09},"
                + "\"fridge_scope\":{\"type\":\"choice\",\"choice\":\"전체\",\"confidence\":0.9,"
                + "\"probabilities\":{\"전체\":0.92,\"밀키트\":0.06,\"임박\":0.01,\"양념\":0.01}}},"
                + "\"usage\":{\"input_tokens\":417,\"output_tokens\":66}}";
    }

    // jev-api-notes.md d의 Noul·맛 답, 요청 종류는 넓은 요청 0.8, 분류는 category(위 CATEGORY_… 중 하나)
    private static String secondResponse(String category) {
        StringBuilder json = new StringBuilder("{\"model\":\"jev-1.14.0\",\"answers\":{")
                .append("\"dish_named\":{\"type\":\"noul\",\"noul\":0.09},")
                .append("\"feature_named\":{\"type\":\"noul\",\"noul\":0.74},")
                .append("\"ingredient_named\":{\"type\":\"noul\",\"noul\":0.07},")
                .append("\"mealkit_wanted\":{\"type\":\"noul\",\"noul\":0.08},")
                .append(taste("taste_spicy", 0.98, 0.0, 0.01, 0.99))
                .append(taste("taste_sweet", 1.0, 0.0, 0.0, 1.0))
                .append(taste("taste_salty", 1.0, 0.0, 0.0, 1.0))
                .append(taste("taste_umami", 0.89, 0.07, 0.0, 0.93))
                .append(taste("taste_mild", 0.97, 0.02, 0.0, 0.98))
                .append("\"request_kind\":{\"type\":\"choice\",\"choice\":\"넓은 요청\",\"confidence\":0.8,\"probabilities\":"
                        + "{\"특정 요리\":0.02,\"특정 분류\":0.12,\"넓은 요청\":0.84,\"없음\":0.02}},")
                .append(category);
        return json.append("},\"usage\":{\"input_tokens\":1893,\"output_tokens\":770}}").toString();
    }

    private static String taste(String name, double confidence, double wanted, double avoided, double notMentioned) {
        return "\"" + name + "\":{\"type\":\"choice\",\"choice\":\"언급 없음\",\"confidence\":" + confidence
                + ",\"probabilities\":{\"원함\":" + wanted + ",\"피함\":" + avoided + ",\"언급 없음\":" + notMentioned + "}},";
    }

    private static Map<String, Object> parse(byte[] body) {
        return map(JSON.readValue(new String(body, StandardCharsets.UTF_8), Map.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}

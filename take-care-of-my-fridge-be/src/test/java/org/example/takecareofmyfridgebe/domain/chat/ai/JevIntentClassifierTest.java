package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
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
import java.util.stream.IntStream;
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

    private static final List<String> CATEGORIES = List.of(
            "과자", "구이", "국", "그라탕", "기타", "김치", "디저트", "떡", "만두", "말이", "면류", "무침", "밥", "볶음", "빵", "샌드위치",
            "샐러드", "수프", "스튜", "쌈", "양념", "음료", "전", "전골", "절임", "조림", "죽", "찌개", "찜", "튀김", "피자", "한과", "회");
    // jev-api-notes.md c (state "따뜻한 국물 요리 추천해줘")의 분류별 noul, CATEGORIES 순서
    private static final double[] CATEGORY_NOULS = {
            0.02, 0.03, 0.83, 0.09, 0.27, 0.11, 0.03, 0.17, 0.12, 0.4, 0.31, 0.05, 0.25, 0.04, 0.02, 0.02,
            0.02, 0.7, 0.42, 0.05, 0.13, 0.06, 0.06, 0.41, 0.03, 0.17, 0.24, 0.46, 0.17, 0.03, 0.02, 0.04, 0.04};

    private static final String FIRST_RECOMMEND = firstResponse(
            "{\"type\":\"choice\",\"choice\":\"추천\",\"confidence\":1.0,\"probabilities\":"
                    + "{\"추천\":1.0,\"일반 질문\":0.0,\"냉장고 보기\":0.0,\"장보기\":0.0,\"상관없는 말\":0.0}}");
    private static final String FIRST_REFINE = firstResponse(
            "{\"type\":\"choice\",\"choice\":\"조건 바꾸기\",\"confidence\":0.88,\"probabilities\":"
                    + "{\"추천\":0.08,\"조건 바꾸기\":0.9,\"레시피 자세히\":0.02}}");
    private static final String FIRST_GENERAL = firstResponse(
            "{\"type\":\"choice\",\"choice\":\"일반 질문\",\"confidence\":0.95,\"probabilities\":"
                    + "{\"추천\":0.03,\"일반 질문\":0.96,\"냉장고 보기\":0.01}}");
    private static final String SECOND = secondResponse();

    private final List<byte[]> sentBodies = new ArrayList<>();
    private MockRestServiceServer server;

    @Test
    @DisplayName("1차 요청은 model·키·UTF-8 한글 state와 의도·묻는 말·냉장고 범위 질문 3개를 보낸다")
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
        assertThat(map(map(questions.get("intent")).get("criteria")).keySet()).containsExactly(
                "추천", "일반 질문", "조건 바꾸기", "레시피 자세히", "냉장고 보기", "장보기", "상관없는 말", "막을 말", "자해");
        assertThat(map(questions.get("asks_question")))
                .containsEntry("type", "noul")
                .containsEntry("instructions", "정보를 묻는 말인가(보관법·손질법·뜻 등). 요리를 정해 달라는 말은 아니다")
                .doesNotContainKey("criteria");
        assertThat(map(map(questions.get("fridge_scope")).get("criteria")).keySet())
                .containsExactly("전체", "밀키트", "임박", "양념");
    }

    @Test
    @DisplayName("1차 의도가 추천이면 2차로 요리 이름·특징·재료·밀키트 Noul, 맛 Choice 5개, 분류마다 Noul을 순번 이름으로 보낸다")
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
                "taste_salty", "taste_sweet", "taste_spicy", "taste_umami", "taste_mild"));
        IntStream.range(0, CATEGORIES.size()).forEach(i -> expectedNames.add("category_" + i));
        assertThat(questions.keySet()).containsExactlyElementsOf(expectedNames);
        assertThat(map(questions.get("dish_named"))).containsEntry("type", "noul")
                .containsEntry("instructions", "특정 요리 이름을 말했나");
        Map<String, Object> spicy = map(questions.get("taste_spicy"));
        assertThat(spicy.get("type")).isEqualTo("choice");
        assertThat((String) spicy.get("instructions")).startsWith("매운맛(매운맛·얼큰한·칼칼한)에 대해");
        assertThat(map(spicy.get("criteria")).keySet()).containsExactly("원함", "피함", "언급 없음");
        assertThat(map(questions.get("category_2"))).containsEntry("type", "noul")
                .containsEntry("instructions", "원하는 요리 분류가 '국'인가");
    }

    @Test
    @DisplayName("1차·2차 답을 읽어 의도·확률·맛·Noul을 담고 분류 순번을 분류 이름으로 되돌린다")
    void readsAnswersAndMapsCategoryNames_whenBothCallsSucceed() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_RECOMMEND, "req_first");
        expectCall(SECOND, "req_second");

        JevIntentJudgement judgement = classifier.classify(MESSAGE, CATEGORIES, null).orElseThrow();

        JevIntentJudgement.First first = judgement.first();
        assertThat(first.intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(first.intentConfidence()).isEqualTo(1.0);
        assertThat(first.intentProbabilities()).hasSize(9)
                .containsEntry(ChatIntent.RECOMMEND, 1.0)
                .containsEntry(ChatIntent.REFINE, 0.0);
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
        assertThat(second.categories().keySet()).containsExactlyElementsOf(CATEGORIES);
        assertThat(second.categories()).containsEntry("국", 0.83).containsEntry("수프", 0.7).containsEntry("말이", 0.4);
        assertThat(second.call().requestId()).isEqualTo("req_second");
        assertThat(second.call().inputTokens()).isEqualTo(1893);
        assertThat(judgement.totalLatencyMs()).isGreaterThanOrEqualTo(first.call().latencyMs() + second.call().latencyMs());
    }

    @Test
    @DisplayName("직전 추천이 있으면 그 정보 뒤에 이번 메시지를 붙인 state로 묻고, 조건 바꾸기면 2차도 부른다")
    void callsSecondWithFollowUpState_whenIntentIsRefine() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_REFINE, "req_first");
        expectCall(SECOND, "req_second");
        String followUp = "[직전 추천] 1. 김치찌개 / 2. 순두부찌개\n[직전 조건] 분류 찌개";

        JevIntentJudgement judgement = classifier.classify("좀 더 매운 걸로", CATEGORIES, followUp).orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.REFINE);
        assertThat(judgement.hasSecond()).isTrue();
        String expectedState = "[직전 추천] 1. 김치찌개 / 2. 순두부찌개\n[직전 조건] 분류 찌개\n[이번 말] 좀 더 매운 걸로";
        assertThat(parse(sentBodies.get(0)).get("state")).isEqualTo(expectedState);
        assertThat(parse(sentBodies.get(1)).get("state")).isEqualTo(expectedState);
    }

    @Test
    @DisplayName("1차 의도가 추천·조건 바꾸기가 아니면 2차를 부르지 않는다")
    void skipsSecond_whenIntentIsGeneral() {
        JevIntentClassifier classifier = classifier(withKey());
        expectCall(FIRST_GENERAL, "req_first");

        JevIntentJudgement judgement = classifier.classify("두부 보관 어떻게 해?", CATEGORIES, null).orElseThrow();

        server.verify();
        assertThat(judgement.first().intent()).isEqualTo(ChatIntent.GENERAL);
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
        return "{\"model\":\"jev-1.14.0\",\"answers\":{"
                + "\"intent\":" + intentAnswer + ","
                + "\"asks_question\":{\"type\":\"noul\",\"noul\":0.09},"
                + "\"fridge_scope\":{\"type\":\"choice\",\"choice\":\"전체\",\"confidence\":0.9,"
                + "\"probabilities\":{\"전체\":0.92,\"밀키트\":0.06,\"임박\":0.01,\"양념\":0.01}}},"
                + "\"usage\":{\"input_tokens\":417,\"output_tokens\":66}}";
    }

    // jev-api-notes.md d의 Noul·맛 답과 c의 분류별 noul
    private static String secondResponse() {
        StringBuilder json = new StringBuilder("{\"model\":\"jev-1.14.0\",\"answers\":{")
                .append("\"dish_named\":{\"type\":\"noul\",\"noul\":0.09},")
                .append("\"feature_named\":{\"type\":\"noul\",\"noul\":0.74},")
                .append("\"ingredient_named\":{\"type\":\"noul\",\"noul\":0.07},")
                .append("\"mealkit_wanted\":{\"type\":\"noul\",\"noul\":0.08},")
                .append(taste("taste_spicy", 0.98, 0.0, 0.01, 0.99))
                .append(taste("taste_sweet", 1.0, 0.0, 0.0, 1.0))
                .append(taste("taste_salty", 1.0, 0.0, 0.0, 1.0))
                .append(taste("taste_umami", 0.89, 0.07, 0.0, 0.93))
                .append(taste("taste_mild", 0.97, 0.02, 0.0, 0.98));
        for (int i = 0; i < CATEGORY_NOULS.length; i++) {
            json.append(i == 0 ? "" : ",")
                    .append("\"category_").append(i).append("\":{\"type\":\"noul\",\"noul\":").append(CATEGORY_NOULS[i]).append('}');
        }
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

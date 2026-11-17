package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.global.config.TypeSafeExecutor;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture.MEMBER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 상담 의도 판단의 Jev 호출: 키 없음·실패면 OpenAI로 되돌리기, 기다리기, 실행기 자리, 결정 로그
@ExtendWith(OutputCaptureExtension.class)
class JevIntentRunnerTest {

    private static final String MESSAGE = "매운 찌개 추천해줘";
    // 테스트용 가짜 키 (실제 키가 아니다)
    private static final String FAKE_KEY = "fake-test-key-0000";
    // ChatIntentFixture의 분류 목록(국, 기타, 샐러드, 양념, 음료, 찌개)에서 후보 제외(양념, 음료)와 기타를 뺀 것
    private static final List<String> CATEGORIES = List.of("국", "샐러드", "찌개");
    // 결정 로그는 Jev 스레드에서 늦게 남을 수 있어, 테스트마다 다른 상담 ID로 자기 로그만 읽는다
    private static final AtomicLong SESSION_IDS = new AtomicLong(1_000);

    private final Long sessionId = SESSION_IDS.incrementAndGet();

    private final ChatIntentFixture fixture = new ChatIntentFixture();
    private final JevIntentClassifier classifier = mock(JevIntentClassifier.class);
    private final TypeSafeExecutor executor = new TypeSafeExecutor(2, 4);
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        when(fixture.extractor.extract(eq(MEMBER_ID), eq(sessionId), any(), any(), any())).thenReturn(new IntentResult(
                ChatIntent.RECOMMEND, List.of("SPICY"), List.of("찌개"), null, null, null, false, null, null, null));
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.close();
    }

    @Test
    @DisplayName("키가 없으면 Jev를 부르지 않고 OpenAI 결과로 되돌리며 결정 로그에 SKIPPED와 OPENAI_JEV_FAILED를 남긴다")
    void fallsBackToOpenAi_whenApiKeyBlank(CapturedOutput output) {
        ChatIntentResolution resolution = facade(runner(client(""))).resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution).isEqualTo(withoutJev());
        verifyNoInteractions(classifier);
        assertThat(JevDecisionLogTest.values(awaitDecisionLog(output, sessionId)))
                .containsEntry("jevStatus", "SKIPPED").containsEntry("decision", "OPENAI_JEV_FAILED");
    }

    @Test
    @DisplayName("키가 없으면 시작할 때 OpenAI로만 되돌린다는 경고를 남기고, 키가 있으면 남기지 않는다")
    void warnsOnce_whenApiKeyBlankAtStartup(CapturedOutput output) {
        runner(client(FAKE_KEY)).warnIfNoKey();
        assertThat(output.getOut()).doesNotContain("TypeSafe 키");

        runner(client("")).warnIfNoKey();
        assertThat(output.getOut()).contains("TypeSafe 키(app.typesafe.api-key, TYPESAFE_API_KEY)가 비어 있어");
    }

    @Test
    @DisplayName("OpenAI와 같은 메시지·분류 목록·직전 추천 정보로 Jev를 부르고, Jev로 정한 결과와 decision=JEV·쓴 요청 종류·값을 결정 로그에 남긴다")
    void decidesWithJevAndLogs_whenJevSucceeds(CapturedOutput output) {
        // 2차: 특정 분류 찌개 (JevDecisionLogTest.judgement)
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(JevDecisionLogTest.judgement(ChatIntent.RECOMMEND, true)));

        ChatIntentResolution resolution = facade(runner(client(FAKE_KEY))).resolve(MEMBER_ID, sessionId, request());

        verify(classifier).classify(MESSAGE, CATEGORIES, null);
        assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
        String line = awaitDecisionLog(output, sessionId);
        assertThat(JevDecisionLogTest.keys(line)).containsExactlyElementsOf(JevDecisionLogTest.FIELDS);
        assertThat(JevDecisionLogTest.values(line)).containsEntry("decision", "JEV").containsEntry("jevUsedKind", "CATEGORY")
                .containsEntry("jevUsedValues", "찌개").containsEntry("finalCategories", "찌개").containsEntry("jevStatus", "OK");
    }

    @Test
    @DisplayName("Jev가 예외로 끝나면 OpenAI 결과로 되돌리고 결정 로그를 FIRST_FAILED와 OPENAI_JEV_FAILED로 남긴다")
    void fallsBackAndLogsFirstFailed_whenJevThrows(CapturedOutput output) {
        when(classifier.classify(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        ChatIntentResolution resolution = facade(runner(client(FAKE_KEY))).resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution).isEqualTo(withoutJev());
        assertThat(JevDecisionLogTest.values(awaitDecisionLog(output, sessionId)))
                .containsEntry("jevStatus", "FIRST_FAILED").containsEntry("decision", "OPENAI_JEV_FAILED")
                .containsEntry("finalIntent", "RECOMMEND");
    }

    @Test
    @DisplayName("결정 로그는 정해진 칸 이름으로 남고 메시지 원문과 키가 없다")
    void logsFixedFieldsWithoutMessageAndKey_whenJevSucceeds(CapturedOutput output) {
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(JevDecisionLogTest.judgement(ChatIntent.RECOMMEND, true)));

        facade(runner(client(FAKE_KEY))).resolve(MEMBER_ID, sessionId, request());

        String line = awaitDecisionLog(output, sessionId);
        assertThat(JevDecisionLogTest.keys(line)).containsExactlyElementsOf(JevDecisionLogTest.FIELDS);
        assertThat(JevDecisionLogTest.values(line))
                .containsEntry("memberId", String.valueOf(MEMBER_ID)).containsEntry("sessionId", String.valueOf(sessionId))
                .containsEntry("messageLength", String.valueOf(MESSAGE.length()))
                .containsEntry("finalIntent", "RECOMMEND").containsEntry("finalWanted", "SPICY").containsEntry("jevStatus", "OK");
        assertThat(line).doesNotContain(MESSAGE);
        assertThat(output.getOut()).doesNotContain(FAKE_KEY);
    }

    @Test
    @DisplayName("실행기 스레드와 대기 줄이 차면 기다리지 않고 건너뛴다")
    void skipsWithoutWaiting_whenExecutorIsFull() {
        TypeSafeExecutor small = new TypeSafeExecutor(1, 1);
        when(classifier.classify(any(), any(), any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Optional.empty();
        });
        JevIntentRunner runner = new JevIntentRunner(classifier, client(FAKE_KEY), small);
        try {
            JevIntentRunner.Run running = runner.start(MESSAGE, CATEGORIES, null);
            verify(classifier, timeout(1_000)).classify(any(), any(), any());
            JevIntentRunner.Run queued = runner.start(MESSAGE, CATEGORIES, null);
            JevIntentRunner.Run skipped = runner.start(MESSAGE, CATEGORIES, null);

            assertThat(running.jev()).isNotDone();
            assertThat(queued.jev()).isNotDone();
            assertThat(skipped.jev()).isCompletedWithValue(JevDecisionLog.JevSide.skipped());
            verify(classifier, times(1)).classify(any(), any(), any());
        } finally {
            release.countDown();
            small.close();
        }
    }

    @Test
    @DisplayName("1차·2차까지 성공한 Jev 판단을 기다려 돌려준다")
    void awaitsJudgement_whenJevSucceeds() {
        JevIntentJudgement judgement = JevDecisionLogTest.judgement(ChatIntent.RECOMMEND, true);
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(judgement));
        JevIntentRunner runner = runner(client(FAKE_KEY));

        assertThat(runner.await(runner.start(MESSAGE, CATEGORIES, null))).contains(judgement);
    }

    @Test
    @DisplayName("추천인데 2차가 실패했으면 Jev 실패로 보고 빈 값이다")
    void returnsEmpty_whenSecondFailed() {
        when(classifier.classify(any(), any(), any()))
                .thenReturn(Optional.of(JevDecisionLogTest.judgement(ChatIntent.RECOMMEND, false)));
        JevIntentRunner runner = runner(client(FAKE_KEY));

        assertThat(runner.await(runner.start(MESSAGE, CATEGORIES, null))).isEmpty();
        assertThat(runner.await(null)).isEmpty();
    }

    @Test
    @DisplayName("Jev를 시작한 때부터 TypeSafe 제한 시간이 지나도 끝나지 않으면 더 기다리지 않고 빈 값이다")
    void returnsEmpty_whenJevExceedsTimeout() {
        when(classifier.classify(any(), any(), any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Optional.of(JevDecisionLogTest.judgement(ChatIntent.RECOMMEND, true));
        });
        JevIntentRunner runner = runner(new TypeSafeClient(RestClient.create(),
                new TypeSafeProperties(null, FAKE_KEY, null, Duration.ofMillis(200))));

        long startedAt = System.nanoTime();
        Optional<JevIntentJudgement> judged = runner.await(runner.start(MESSAGE, CATEGORIES, null));

        assertThat(judged).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private JevIntentRunner runner(TypeSafeClient client) {
        return new JevIntentRunner(classifier, client, executor);
    }

    private ChatIntentFacade facade(JevIntentRunner runner) {
        return fixture.newFacade(runner);
    }

    // 실제로 부르지 않는다 (분류기를 흉내 낸다). 키가 있는지(ready)만 본다
    private static TypeSafeClient client(String apiKey) {
        return new TypeSafeClient(RestClient.create(), new TypeSafeProperties(null, apiKey, null, null));
    }

    // Jev 없이(키 없음과 같은 되돌림) 정한 결과
    private ChatIntentResolution withoutJev() {
        return fixture.facade.resolve(MEMBER_ID, sessionId, request());
    }

    private static ChatMessageCreateRequest request() {
        return ChatMessageCreateRequest.of(MESSAGE);
    }

    // 결정 로그는 Jev 스레드에서 남으므로 나올 때까지 잠깐 기다린다
    private static String awaitDecisionLog(CapturedOutput output, Long sessionId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            Optional<String> line = Arrays.stream(output.getOut().split("\\R"))
                    .filter(each -> each.contains(JevDecisionLog.NAME + " - ") && each.contains(" sessionId=" + sessionId + " "))
                    .map(each -> each.substring(each.indexOf(JevDecisionLog.NAME)))
                    .findFirst();
            if (line.isPresent()) {
                return line.get();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("결정 로그가 나오지 않음");
    }
}

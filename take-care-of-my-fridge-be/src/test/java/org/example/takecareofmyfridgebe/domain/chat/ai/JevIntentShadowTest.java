package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatJevProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.SimilarDishFinder;
import org.example.takecareofmyfridgebe.domain.chat.intent.JevIntentResolver;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.global.config.TypeSafeShadowExecutor;
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

// 상담 의도 판단에 붙인 Jev 그림자 모드: 켜고 끄기, 상담 결과가 그대로인지, 기다리지 않는지, 비교 로그
@ExtendWith(OutputCaptureExtension.class)
class JevIntentShadowTest {

    private static final String MESSAGE = "매운 찌개 추천해줘";
    // 테스트용 가짜 키 (실제 키가 아니다)
    private static final String FAKE_KEY = "fake-test-key-0000";
    // ChatIntentFixture의 분류 목록(국, 기타, 샐러드, 양념, 음료, 찌개)에서 후보 제외(양념, 음료)와 기타를 뺀 것
    private static final List<String> CATEGORIES = List.of("국", "샐러드", "찌개");
    // 비교 로그는 Jev 스레드에서 늦게 남을 수 있어, 테스트마다 다른 상담 ID로 자기 로그만 읽는다
    private static final AtomicLong SESSION_IDS = new AtomicLong(1_000);

    private final Long sessionId = SESSION_IDS.incrementAndGet();

    private final ChatIntentFixture fixture = new ChatIntentFixture();
    private final JevIntentClassifier classifier = mock(JevIntentClassifier.class);
    private final TypeSafeShadowExecutor executor = new TypeSafeShadowExecutor(2, 4);
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
    @DisplayName("꺼져 있으면 Jev를 부르지 않고 비교 로그도 남기지 않는다")
    void skipsJev_whenDisabled(CapturedOutput output) {
        ChatIntentResolution resolution = facade(false, FAKE_KEY).resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution).isEqualTo(withoutJev());
        verifyNoInteractions(classifier);
        assertThat(output.getOut()).doesNotContain(JevComparisonLog.NAME);
    }

    @Test
    @DisplayName("켜져 있으면 OpenAI와 같은 메시지·분류 목록·직전 추천 정보로 Jev를 부르고, 상담 결과는 꺼져 있을 때와 같다")
    void callsJevAndKeepsResolution_whenEnabled() {
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(JevComparisonLogTest.judgement(ChatIntent.RECOMMEND, true)));

        ChatIntentResolution resolution = facade(true, FAKE_KEY).resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution).isEqualTo(withoutJev());
        verify(classifier, timeout(1_000)).classify(MESSAGE, CATEGORIES, null);
    }

    @Test
    @DisplayName("Jev가 끝나지 않아도 상담 결과를 바로 돌려준다")
    void returnsWithoutWaiting_whenJevIsSlow() throws InterruptedException {
        when(classifier.classify(any(), any(), any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Optional.empty();
        });

        long startedAt = System.nanoTime();
        ChatIntentResolution resolution = facade(true, FAKE_KEY).resolve(MEMBER_ID, sessionId, request());
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(resolution).isEqualTo(withoutJev());
        verify(classifier, timeout(1_000)).classify(any(), any(), any());
        assertThat(release.getCount()).isEqualTo(1);
        assertThat(elapsedMs).isLessThan(1_000);
    }

    @Test
    @DisplayName("Jev가 예외로 끝나도 상담 결과는 같고 비교 로그를 FIRST_FAILED로 남긴다")
    void keepsResolutionAndLogsFirstFailed_whenJevThrows(CapturedOutput output) {
        when(classifier.classify(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        ChatIntentResolution resolution = facade(true, FAKE_KEY).resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution).isEqualTo(withoutJev());
        assertThat(JevComparisonLogTest.values(awaitComparison(output, sessionId)))
                .containsEntry("jevStatus", "FIRST_FAILED").containsEntry("openaiIntent", "RECOMMEND");
    }

    @Test
    @DisplayName("on: Jev 결과로 정한 상담 결과와 decision=JEV·쓴 요청 종류·값을 비교 로그에 남긴다")
    void logsJevDecision_whenOnModeDecides(CapturedOutput output) {
        // 2차: 특정 분류 찌개 (JevComparisonLogTest.judgement)
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(JevComparisonLogTest.judgement(ChatIntent.RECOMMEND, true)));
        ChatCorrectionProperties corrections = ChatCorrectionProperties.ALL_ON;
        ChatIntentFacade facade = new ChatIntentFacade(fixture.extractor, fixture.recipeService, fixture.ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                ChatIntentFixture.WORDS, fixture.allergyExclusionFinder, corrections, fixture.newCorrector(corrections),
                onShadow(client(FAKE_KEY)), new JevIntentResolver(mock(SimilarDishFinder.class), ChatIntentFixture.WORDS));

        ChatIntentResolution resolution = facade.resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution.recipeQuery().moodCategories()).containsExactly("찌개");
        String line = awaitComparison(output, sessionId);
        assertThat(JevComparisonLogTest.keys(line)).containsExactlyElementsOf(JevComparisonLogTest.FIELDS);
        assertThat(JevComparisonLogTest.values(line)).containsEntry("decision", "JEV").containsEntry("jevUsedKind", "CATEGORY")
                .containsEntry("jevUsedValues", "찌개").containsEntry("openaiCategories", "찌개").containsEntry("jevStatus", "OK");
        assertThat(line).doesNotContain(MESSAGE).doesNotContain(FAKE_KEY);
    }

    @Test
    @DisplayName("비교 로그는 정해진 칸 이름으로 남고 메시지 원문과 키가 없다")
    void logsFixedFieldsWithoutMessageAndKey_whenJevSucceeds(CapturedOutput output) {
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(JevComparisonLogTest.judgement(ChatIntent.RECOMMEND, true)));

        facade(true, FAKE_KEY).resolve(MEMBER_ID, sessionId, request());

        String line = awaitComparison(output, sessionId);
        assertThat(JevComparisonLogTest.keys(line)).containsExactlyElementsOf(JevComparisonLogTest.FIELDS);
        assertThat(JevComparisonLogTest.values(line))
                .containsEntry("memberId", String.valueOf(MEMBER_ID)).containsEntry("sessionId", String.valueOf(sessionId))
                .containsEntry("messageLength", String.valueOf(MESSAGE.length()))
                .containsEntry("openaiIntent", "RECOMMEND").containsEntry("openaiWanted", "SPICY")
                .containsEntry("openaiCategories", "찌개").containsEntry("jevStatus", "OK");
        assertThat(line).doesNotContain(MESSAGE);
        assertThat(output.getOut()).doesNotContain(FAKE_KEY);
    }

    @Test
    @DisplayName("켜져 있어도 키가 없으면 Jev를 부르지 않고 SKIPPED로 남긴다")
    void logsSkipped_whenApiKeyBlank(CapturedOutput output) {
        ChatIntentResolution resolution = facade(true, "").resolve(MEMBER_ID, sessionId, request());

        assertThat(resolution).isEqualTo(withoutJev());
        verifyNoInteractions(classifier);
        assertThat(JevComparisonLogTest.values(awaitComparison(output, sessionId))).containsEntry("jevStatus", "SKIPPED");
    }

    @Test
    @DisplayName("실행기 스레드와 대기 줄이 차면 기다리지 않고 건너뛴다")
    void skipsWithoutWaiting_whenExecutorIsFull() {
        TypeSafeShadowExecutor small = new TypeSafeShadowExecutor(1, 1);
        when(classifier.classify(any(), any(), any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Optional.empty();
        });
        JevIntentShadow shadow = new JevIntentShadow(new ChatJevProperties(ChatJevProperties.Mode.SHADOW), classifier, client(FAKE_KEY), small);
        try {
            JevIntentShadow.Run running = shadow.start(MESSAGE, CATEGORIES, null);
            verify(classifier, timeout(1_000)).classify(any(), any(), any());
            JevIntentShadow.Run queued = shadow.start(MESSAGE, CATEGORIES, null);
            JevIntentShadow.Run skipped = shadow.start(MESSAGE, CATEGORIES, null);

            assertThat(running.jev()).isNotDone();
            assertThat(queued.jev()).isNotDone();
            assertThat(skipped.jev()).isCompletedWithValue(JevComparisonLog.JevSide.skipped());
            verify(classifier, times(1)).classify(any(), any(), any());
        } finally {
            release.countDown();
            small.close();
        }
    }

    private ChatIntentFacade facade(boolean enabled, String apiKey) {
        ChatCorrectionProperties corrections = ChatCorrectionProperties.ALL_ON;
        JevIntentShadow shadow = new JevIntentShadow(new ChatJevProperties(enabled ? ChatJevProperties.Mode.SHADOW : ChatJevProperties.Mode.OFF), classifier, client(apiKey), executor);
        return new ChatIntentFacade(fixture.extractor, fixture.recipeService, fixture.ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                ChatIntentFixture.WORDS, fixture.allergyExclusionFinder, corrections, fixture.newCorrector(corrections), shadow);
    }

    @Test
    @DisplayName("on: 1차·2차까지 성공한 Jev 판단을 기다려 돌려준다")
    void awaitsJudgement_whenJevSucceeds() {
        JevIntentJudgement judgement = JevComparisonLogTest.judgement(ChatIntent.RECOMMEND, true);
        when(classifier.classify(any(), any(), any())).thenReturn(Optional.of(judgement));
        JevIntentShadow shadow = onShadow(client(FAKE_KEY));

        assertThat(shadow.on()).isTrue();
        assertThat(shadow.await(shadow.start(MESSAGE, CATEGORIES, null))).contains(judgement);
    }

    @Test
    @DisplayName("on: 추천인데 2차가 실패했으면 Jev 실패로 보고 빈 값이다")
    void returnsEmpty_whenSecondFailed() {
        when(classifier.classify(any(), any(), any()))
                .thenReturn(Optional.of(JevComparisonLogTest.judgement(ChatIntent.RECOMMEND, false)));
        JevIntentShadow shadow = onShadow(client(FAKE_KEY));

        assertThat(shadow.await(shadow.start(MESSAGE, CATEGORIES, null))).isEmpty();
        assertThat(shadow.await(null)).isEmpty();
    }

    @Test
    @DisplayName("on: Jev를 시작한 때부터 TypeSafe 제한 시간이 지나도 끝나지 않으면 더 기다리지 않고 빈 값이다")
    void returnsEmpty_whenJevExceedsTimeout() {
        when(classifier.classify(any(), any(), any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Optional.of(JevComparisonLogTest.judgement(ChatIntent.RECOMMEND, true));
        });
        JevIntentShadow shadow = onShadow(new TypeSafeClient(RestClient.create(),
                new TypeSafeProperties(null, FAKE_KEY, null, Duration.ofMillis(200))));

        long startedAt = System.nanoTime();
        Optional<JevIntentJudgement> judged = shadow.await(shadow.start(MESSAGE, CATEGORIES, null));

        assertThat(judged).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private JevIntentShadow onShadow(TypeSafeClient client) {
        return new JevIntentShadow(new ChatJevProperties(ChatJevProperties.Mode.ON), classifier, client, executor);
    }

    // 실제로 부르지 않는다 (분류기를 흉내 낸다). 키가 있는지(ready)만 본다
    private static TypeSafeClient client(String apiKey) {
        return new TypeSafeClient(RestClient.create(), new TypeSafeProperties(null, apiKey, null, null));
    }

    private ChatIntentResolution withoutJev() {
        return fixture.facade.resolve(MEMBER_ID, sessionId, request());
    }

    private static ChatMessageCreateRequest request() {
        return ChatMessageCreateRequest.of(MESSAGE);
    }

    // 비교 로그는 Jev 스레드에서 남으므로 나올 때까지 잠깐 기다린다
    private static String awaitComparison(CapturedOutput output, Long sessionId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            Optional<String> line = Arrays.stream(output.getOut().split("\\R"))
                    .filter(each -> each.contains(JevComparisonLog.NAME + " - ") && each.contains(" sessionId=" + sessionId + " "))
                    .map(each -> each.substring(each.indexOf(JevComparisonLog.NAME)))
                    .findFirst();
            if (line.isPresent()) {
                return line.get();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("비교 로그가 나오지 않음");
    }
}

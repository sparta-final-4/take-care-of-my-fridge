package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevAnswers.JevAnswerException;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneCall;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Jev(TypeSafe 분류 모델)로 상담 메시지의 의도 판단 질문에 답을 받는다. 상담 의도는 이 판단으로 정한다 (JevIntentRunner·JevIntentResolver).
 * <ol>
 *   <li>1차(모든 메시지): 의도, 정보를 묻는 말인지, 직전 추천이 있으면 이어지는 말인지 (JevFirstQuestions)</li>
 *   <li>2차(1차 의도가 RECOMMEND일 때만): 요리 이름·특징·재료·밀키트, 맛 5개, 요청 종류, 1등 분류 (JevSecondQuestions)</li>
 * </ol>
 * 1차 의도에는 서비스 규칙을 적용한다 (serviceIntent): 직전 추천이 없는데 RECIPE_DETAIL로 답하면 RECOMMEND로 보고(ChatIntentFacade와 같다) 2차도 묻는다.
 * 직전 추천이 있고 추천 + 이어지는 말(follows_previous ≥ {@value #MIN_FOLLOWS_PREVIOUS})이면 추천 안의 이어짐 항목으로 넘긴다 ({@link #follows}).
 * 결정 로그(JevDecisionLog)와 평가(JevIntentEvalManualTest)가 이 값을 그대로 쓴다.
 * 1차가 실패하면 빈 값, 2차만 실패하면 1차만 있는 결과다. 예외는 밖으로 내지 않고 WARN 로그를 남긴다.
 * HTTP 실패 로그는 TypeSafeClient가, 답 형식 오류 로그는 여기서 남긴다. 로그에 메시지 원문은 쓰지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JevIntentClassifier {

    static final String FIRST = "의도 1차";
    static final String SECOND = "의도 2차";
    // 직전 추천 정보 뒤에 이번 메시지를 붙일 때 쓰는 머리말
    static final String MESSAGE_HEADER = "\n[이번 말] ";
    private static final Set<ChatIntent> NEEDS_SECOND = Set.of(ChatIntent.RECOMMEND);
    // 직전 추천이 있어야 뜻이 있는 의도. 직전 추천이 없으면 새 추천으로 본다
    private static final Set<ChatIntent> FOLLOW_UP_INTENTS = Set.of(ChatIntent.RECIPE_DETAIL);
    // 추천을 직전 추천에 이어지는 추천(이어짐)으로 볼 follows_previous "예" 확률. 평가로 정함
    static final double MIN_FOLLOWS_PREVIOUS = 0.45;
    // 2차를 부르려면 끝 시각까지 남아 있어야 할 시간. 2차 한 번은 보통 0.2초 안팎(jev-v4 1·2차 합계 95% 417ms)이라
    // 이보다 적게 남았으면 부르더라도 상담이 기다리지 못하고 비용만 든다
    static final Duration MIN_TIME_FOR_SECOND = Duration.ofMillis(500);
    // 끝 시각 없음 (평가처럼 기다리는 쪽이 없을 때)
    static final long NO_DEADLINE = Long.MAX_VALUE;

    private final TypeSafeClient typeSafeClient;

    /**
     * @param message    사용자 메시지
     * @param categories 고를 수 있는 레시피 분류 (RecipeService.getClassifications()에서 받은 것)
     * @param followUp   직전 추천 정보 줄 글(ChatIntentFacade.followUp, OpenAI 의도 판단과 같은 것). 첫 질문이면 null
     * @return 판단 결과. 키가 없거나 1차가 실패하면 빈 값 (부르는 쪽이 서버 답으로 답한다)
     */
    public Optional<JevIntentJudgement> classify(String message, List<String> categories, String followUp) {
        return classify(message, categories, followUp, NO_DEADLINE);
    }

    /**
     * 끝 시각이 있는 판단. 1차가 끝난 뒤 끝 시각까지 {@link #MIN_TIME_FOR_SECOND} 넘게 남지 않았거나 스레드가 인터럽트됐으면
     * 2차를 부르지 않고 2차 실패와 같게 1차만 돌려준다 (부르는 쪽은 1차 자해 판단을 본 뒤 Jev 실패로 처리한다).
     *
     * @param deadline 상담이 Jev를 기다리는 끝 시각 (System.nanoTime 기준)
     */
    public Optional<JevIntentJudgement> classify(String message, List<String> categories, String followUp, long deadline) {
        long startedAt = System.nanoTime();
        String state = state(message, followUp);
        boolean hasFollowUp = followUp != null && !followUp.isBlank();

        Optional<JevIntentJudgement.First> first = typeSafeClient.ask(FIRST, state, JevFirstQuestions.questions(hasFollowUp))
                .flatMap(call -> read(FIRST, call, answered -> JevFirstQuestions.read(answered, hasFollowUp)));
        if (first.isEmpty()) {
            return Optional.empty();
        }

        JevIntentJudgement.Second second = null;
        if (asksSecond(first.get().intent()) && !enoughTimeForSecond(deadline)) {
            log.warn("Jev 2차를 부르지 않음 - 남은 시간 부족 또는 취소, 메시지 길이: {}", message.length());
        } else if (asksSecond(first.get().intent())) {
            second = typeSafeClient.ask(SECOND, state, JevSecondQuestions.questions(categories))
                    .flatMap(call -> read(SECOND, call, answered -> JevSecondQuestions.read(answered, categories)))
                    .orElse(null);
            if (second == null) {
                log.warn("Jev 2차 실패로 1차 결과만 돌려줌 - intent: {}, 메시지 길이: {}", first.get().intent(), message.length());
            }
        }

        JevIntentJudgement judgement = new JevIntentJudgement(first.get(), second, elapsedMs(startedAt));
        logJudgement(judgement, message.length());
        return Optional.of(judgement);
    }

    /**
     * 서비스 규칙을 적용한 의도: 직전 추천이 없는데 RECIPE_DETAIL이면 RECOMMEND다
     * (이 상담 추천에 없는 레시피 자세히는 그 요리로 추천). 이어짐은 의도가 아니라 추천 안의 항목이다 ({@link #follows}).
     */
    static ChatIntent serviceIntent(ChatIntent answered, boolean hasFollowUp) {
        if (!hasFollowUp) {
            return FOLLOW_UP_INTENTS.contains(answered) ? ChatIntent.RECOMMEND : answered;
        }
        return answered;
    }

    /**
     * 이어짐: 추천이고 직전 추천에 이어지는 말(follows_previous ≥ {@value #MIN_FOLLOWS_PREVIOUS})인지.
     * 직전 추천이 없어 묻지 않았으면(followsPrevious null) 아니오다.
     */
    public static boolean follows(JevIntentJudgement.First first) {
        return first.intent() == ChatIntent.RECOMMEND && first.followsPrevious() != null
                && first.followsPrevious() >= MIN_FOLLOWS_PREVIOUS;
    }

    // 끝 시각까지 2차를 부를 시간이 남았는지. 상담이 이미 포기했으면(인터럽트) 부르지 않는다
    static boolean enoughTimeForSecond(long deadline) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        return deadline == NO_DEADLINE || deadline - System.nanoTime() > MIN_TIME_FOR_SECOND.toNanos();
    }

    // 2차(추천 조건)를 묻는 의도인지 (서비스 규칙을 적용한 의도로 본다)
    public static boolean asksSecond(ChatIntent intent) {
        return NEEDS_SECOND.contains(intent);
    }

    // OpenAI 의도 판단처럼 직전 추천이 있을 때만 그 정보를 앞에 둔다
    static String state(String message, String followUp) {
        if (followUp == null || followUp.isBlank()) {
            return message;
        }
        return followUp.strip() + MESSAGE_HEADER + message;
    }

    private <T> Optional<T> read(String purpose, SystemOneCall call, Function<SystemOneCall, T> reader) {
        try {
            return Optional.of(reader.apply(call));
        } catch (JevAnswerException e) {
            log.warn("Jev {} 답 형식 오류 - requestId: {}, 원인: {}", purpose, call.requestId(), e.getMessage());
            return Optional.empty();
        }
    }

    private void logJudgement(JevIntentJudgement judgement, int messageLength) {
        JevIntentJudgement.First first = judgement.first();
        JevIntentJudgement.Second second = judgement.second();
        log.info("Jev 의도 판단 - intent: {} (Jev 답 {}), confidence: {}, 이어짐: {}, 메시지 길이: {}, model: {}, "
                        + "1차: {}ms·입력 {}·{}, 2차: {}, 합계: {}ms",
                first.intent(), first.answeredIntent(), first.intentConfidence(), first.followsPrevious(), messageLength, first.call().model(),
                first.call().latencyMs(), first.call().inputTokens(), first.call().requestId(),
                second == null ? "없음" : second.call().latencyMs() + "ms·입력 " + second.call().inputTokens() + "·" + second.call().requestId(),
                judgement.totalLatencyMs());
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

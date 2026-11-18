package org.example.takecareofmyfridgebe.domain.chat.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevDecisionLog.FinalSide;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevDecisionLog.JevSide;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.global.config.TypeSafeExecutor;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 상담 의도 판단의 Jev 호출. OpenAI 꺼내기와 함께 Jev를 다른 스레드에서 시작하고({@link #start}), OpenAI 꺼내기가 끝나면
 * 제한 시간 안에서 결과를 기다린다({@link #await}). 상담 의도를 무엇으로 정했는지는 "상담 의도 Jev 결정" 로그로 남긴다(JevDecisionLog).
 * <ul>
 *   <li>TypeSafe 키가 비었으면 부르지 않고(SKIPPED) 상담은 서버 답(추천 카드)으로 답한다. 시작할 때 경고를 한 번 남긴다</li>
 *   <li>결정 로그는 Jev가 끝난 스레드(이미 끝났으면 부른 스레드)에서 남긴다</li>
 *   <li>예외를 밖으로 내지 않는다. Jev가 실패해도 결정 로그를 같은 형식으로 남긴다(jevStatus)</li>
 *   <li>스레드는 TypeSafeExecutor(크기 고정)를 쓰고, 자리가 없으면 기다리지 않고 건너뛴다(SKIPPED, Jev 실패)</li>
 *   <li>제한 시간이 지나면 작업을 취소한다. 2차는 끝 시각까지 남은 시간이 모자라면 부르지 않는다(JevIntentClassifier)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JevIntentRunner {

    private final JevIntentClassifier jevIntentClassifier;
    private final TypeSafeClient typeSafeClient;
    private final TypeSafeExecutor typeSafeExecutor;

    /**
     * 시작한 Jev 판단.
     *
     * @param startedAt 시작한 때 (System.nanoTime). 기다릴 시간을 셀 때 쓴다
     */
    public record Run(CompletableFuture<JevSide> jev, long startedAt) {

        public Run(CompletableFuture<JevSide> jev) {
            this(jev, System.nanoTime());
        }
    }

    // 키가 없으면 상담 의도를 정하지 못해 항상 서버 답으로 답한다는 것을 시작할 때 한 번 알린다
    @PostConstruct
    void warnIfNoKey() {
        if (!typeSafeClient.ready()) {
            log.warn("TypeSafe 키(app.typesafe.api-key, TYPESAFE_API_KEY)가 비어 있어 상담 의도를 정하지 못하고 항상 서버 답(추천 카드)으로 답한다");
        }
    }

    /**
     * Jev 판단을 다른 스레드에서 시작하고 바로 돌아온다. 키가 없거나 실행기 자리가 없으면 건너뛴 판단(SKIPPED)이다.
     *
     * @param message    사용자 메시지
     * @param categories 레시피 분류 목록 (Jev 분류 선택지)
     * @param followUp   OpenAI 꺼내기에 넘긴 직전 추천 정보 (없으면 null)
     */
    public Run start(String message, List<String> categories, String followUp) {
        try {
            if (!typeSafeClient.ready()) {
                return skipped();
            }
            long startedAt = System.nanoTime();
            // 상담이 기다리는 끝 시각. 대기 줄에서 기다린 시간도 포함한다 (2차를 부를지 이 시각으로 정한다)
            long deadline = startedAt + typeSafeClient.timeout().toNanos();
            return typeSafeExecutor
                    .trySupply(() -> JevSide.finished(jevIntentClassifier.classify(message, categories, followUp, deadline),
                            elapsedMs(startedAt)))
                    .map(future -> new Run(future, startedAt))
                    .orElseGet(() -> {
                        // 기다리지 않고 바로 Jev 실패(서버 답)다
                        log.warn("Jev 판단 건너뜀 - 실행기 자리 없음, 메시지 길이: {}", message.length());
                        return skipped();
                    });
        } catch (RuntimeException e) {
            log.warn("Jev 판단 시작 실패 - 원인: {}", e.getClass().getSimpleName());
            return skipped();
        }
    }

    /**
     * Jev 결과를 기다린다. Jev를 시작한 때부터 TypeSafe 제한 시간(app.typesafe.timeout)이 지나면 더 기다리지 않는다.
     *
     * @param run start의 결과
     * @return 1차가 성공한 Jev 판단. 2차가 필요한데 실패했으면 2차 없이 돌려준다(부르는 쪽이 1차 자해 판단을 본 뒤 Jev 실패로 처리한다).
     *         run이 없거나, 1차가 실패·건너뜀이거나, 제한 시간 안에 끝나지 않았으면 빈 값 (Jev 실패로 본다)
     */
    public Optional<JevIntentJudgement> await(Run run) {
        if (run == null) {
            return Optional.empty();
        }
        long remainingNanos = typeSafeClient.timeout().toNanos() - (System.nanoTime() - run.startedAt());
        try {
            JevSide side = run.jev().get(Math.max(0, remainingNanos), TimeUnit.NANOSECONDS);
            return side.status() == JevDecisionLog.Status.OK || side.status() == JevDecisionLog.Status.SECOND_FAILED
                    ? side.judgement() : Optional.empty();
        } catch (TimeoutException e) {
            // 더 기다리지 않으므로 작업을 취소한다: 대기 줄에 있으면 돌지 않고, 돌고 있으면 인터럽트한다 (TypeSafeExecutor)
            run.jev().cancel(true);
            log.warn("Jev 판단이 제한 시간 안에 끝나지 않아 취소하고 Jev 실패로 - 제한 시간: {}ms", typeSafeClient.timeout().toMillis());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | RuntimeException e) {
            log.warn("Jev 판단 기다리기 실패 - 원인: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Jev가 끝나면(이미 끝났으면 바로) 결정 로그를 남긴다. 기다리지 않는다.
     *
     * @param run        start의 결과 (null이면 아무것도 하지 않는다)
     * @param resolution 상담 최종 결과
     * @param mealkit    밀키트만 보여 줄 추천인지
     * @param decision   상담 의도를 무엇으로 정했는지
     */
    public void logWhenDone(Run run, Long memberId, Long sessionId, int messageLength,
                            ChatIntentResolution resolution, boolean mealkit, JevDecision decision) {
        if (run == null) {
            return;
        }
        try {
            FinalSide result = FinalSide.of(memberId, sessionId, messageLength, resolution, mealkit);
            run.jev().whenComplete((jev, error) -> logDecision(result, error == null ? jev : JevSide.failed(), decision));
        } catch (RuntimeException e) {
            log.warn("Jev 결정 로그 준비 실패 - memberId: {}, sessionId: {}, 원인: {}", memberId, sessionId, e.getClass().getSimpleName());
        }
    }

    private void logDecision(FinalSide result, JevSide jev, JevDecision decision) {
        try {
            log.info("{}", JevDecisionLog.line(result, jev, decision));
        } catch (RuntimeException e) {
            log.warn("Jev 결정 로그 실패 - memberId: {}, sessionId: {}, 원인: {}",
                    result.memberId(), result.sessionId(), e.getClass().getSimpleName());
        }
    }

    private static Run skipped() {
        return new Run(CompletableFuture.completedFuture(JevSide.skipped()));
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

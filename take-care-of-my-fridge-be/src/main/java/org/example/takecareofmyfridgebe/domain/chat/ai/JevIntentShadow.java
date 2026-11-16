package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevComparisonLog.JevSide;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevComparisonLog.OpenAiSide;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatJevProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.global.config.TypeSafeShadowExecutor;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 상담 의도 판단의 Jev 그림자 모드. app.chat.jev.mode가 shadow·on일 때 OpenAI 의도 판단과 함께 Jev를 다른 스레드에서 부르고,
 * 두 결과가 모두 나오면 "상담 의도 Jev 비교" 로그를 한 줄 남긴다(JevComparisonLog).
 * <ul>
 *   <li>shadow: 결과는 상담에 쓰지 않고, 상담 응답은 Jev를 기다리지 않는다</li>
 *   <li>on: ChatIntentFacade가 OpenAI 의도 판단이 끝난 뒤 {@link #await}로 Jev 결과를 기다린다 (Jev를 시작한 때부터 TypeSafe 제한 시간까지)</li>
 *   <li>비교 로그는 Jev가 끝난 스레드(이미 끝났으면 부른 스레드)에서 남긴다</li>
 *   <li>예외를 밖으로 내지 않는다. Jev가 실패해도 비교 로그를 같은 형식으로 남긴다(jevStatus)</li>
 *   <li>스레드는 TypeSafeShadowExecutor(크기 고정)를 쓰고, 자리가 없으면 건너뛴다(SKIPPED)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JevIntentShadow {

    private final ChatJevProperties properties;
    private final JevIntentClassifier jevIntentClassifier;
    private final TypeSafeClient typeSafeClient;
    private final TypeSafeShadowExecutor typeSafeShadowExecutor;

    /**
     * 시작한 Jev 판단. 꺼져 있으면 만들지 않는다(null).
     *
     * @param startedAt 시작한 때 (System.nanoTime). on에서 기다릴 시간을 셀 때 쓴다
     */
    public record Run(CompletableFuture<JevSide> jev, long startedAt) {

        public Run(CompletableFuture<JevSide> jev) {
            this(jev, System.nanoTime());
        }
    }

    // 꺼진 그림자 모드. 아무것도 부르지 않는다 (ChatIntentFacade를 그림자 모드 없이 만들 때)
    public static JevIntentShadow off() {
        return new JevIntentShadow(ChatJevProperties.OFF, null, null, null);
    }

    // on 모드인지 (Jev 판단으로 상담 의도를 정하는지)
    public boolean on() {
        return properties.on();
    }

    /**
     * 켜져 있으면 Jev 판단을 다른 스레드에서 시작하고 바로 돌아온다.
     *
     * @param message    사용자 메시지
     * @param categories OpenAI 의도 판단에 넘긴 분류 목록
     * @param followUp   OpenAI 의도 판단에 넘긴 직전 추천 정보 (없으면 null)
     * @return 시작한 판단. 꺼져 있으면 null (비교 로그도 남기지 않는다)
     */
    public Run start(String message, List<String> categories, String followUp) {
        if (!properties.calls()) {
            return null;
        }
        try {
            if (!typeSafeClient.ready()) {
                return skipped();
            }
            long startedAt = System.nanoTime();
            return typeSafeShadowExecutor
                    .trySupply(() -> JevSide.finished(jevIntentClassifier.classify(message, categories, followUp), elapsedMs(startedAt)))
                    .map(future -> new Run(future, startedAt))
                    .orElseGet(() -> {
                        log.warn("Jev 그림자 판단 건너뜀 - 실행기 자리 없음, 메시지 길이: {}", message.length());
                        return skipped();
                    });
        } catch (RuntimeException e) {
            log.warn("Jev 그림자 판단 시작 실패 - 원인: {}", e.getClass().getSimpleName());
            return skipped();
        }
    }

    /**
     * on: Jev 결과를 기다린다. Jev를 시작한 때부터 TypeSafe 제한 시간(app.typesafe.timeout)이 지나면 더 기다리지 않는다.
     *
     * @param run start의 결과
     * @return 1차와 (필요하면) 2차까지 성공한 Jev 판단. run이 없거나, Jev가 실패·건너뜀이거나, 2차가 필요한데 실패했거나,
     *         제한 시간 안에 끝나지 않았으면 빈 값 (Jev 실패로 본다)
     */
    public Optional<JevIntentJudgement> await(Run run) {
        if (run == null) {
            return Optional.empty();
        }
        long remainingNanos = typeSafeClient.timeout().toNanos() - (System.nanoTime() - run.startedAt());
        try {
            JevSide side = run.jev().get(Math.max(0, remainingNanos), TimeUnit.NANOSECONDS);
            return side.status() == JevComparisonLog.Status.OK ? side.judgement() : Optional.empty();
        } catch (TimeoutException e) {
            log.warn("Jev 판단이 제한 시간 안에 끝나지 않아 OpenAI 결과로 - 제한 시간: {}ms", typeSafeClient.timeout().toMillis());
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
     * Jev가 끝나면(이미 끝났으면 바로) 비교 로그를 남긴다. 기다리지 않는다. 그림자 모드(decision=SHADOW)로 남긴다.
     *
     * @param run        start의 결과 (null이면 아무것도 하지 않는다)
     * @param resolution 상담 최종 결과
     * @param mealkit    밀키트만 보여 줄 추천인지
     */
    public void compareWhenDone(Run run, Long memberId, Long sessionId, int messageLength,
                                ChatIntentResolution resolution, boolean mealkit) {
        compareWhenDone(run, memberId, sessionId, messageLength, resolution, mealkit, JevDecision.SHADOW);
    }

    /**
     * @param decision 상담 의도를 무엇으로 정했는지 (on 모드)
     */
    public void compareWhenDone(Run run, Long memberId, Long sessionId, int messageLength,
                                ChatIntentResolution resolution, boolean mealkit, JevDecision decision) {
        if (run == null) {
            return;
        }
        try {
            OpenAiSide openai = OpenAiSide.of(memberId, sessionId, messageLength, resolution, mealkit);
            run.jev().whenComplete((jev, error) -> logComparison(openai, error == null ? jev : JevSide.failed(), decision));
        } catch (RuntimeException e) {
            log.warn("Jev 비교 준비 실패 - memberId: {}, sessionId: {}, 원인: {}", memberId, sessionId, e.getClass().getSimpleName());
        }
    }

    private void logComparison(OpenAiSide openai, JevSide jev, JevDecision decision) {
        try {
            log.info("{}", JevComparisonLog.line(openai, jev, decision));
        } catch (RuntimeException e) {
            log.warn("Jev 비교 로그 실패 - memberId: {}, sessionId: {}, 원인: {}",
                    openai.memberId(), openai.sessionId(), e.getClass().getSimpleName());
        }
    }

    private static Run skipped() {
        return new Run(CompletableFuture.completedFuture(JevSide.skipped()));
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

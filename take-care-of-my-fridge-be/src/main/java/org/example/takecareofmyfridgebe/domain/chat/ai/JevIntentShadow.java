package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevComparisonLog.JevSide;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevComparisonLog.OpenAiSide;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatJevProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.global.config.TypeSafeShadowExecutor;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 상담 의도 판단의 Jev 그림자 모드. app.chat.jev.mode가 shadow·on일 때 OpenAI 의도 판단과 함께 Jev를 다른 스레드에서 부르고,
 * 두 결과가 모두 나오면 "상담 의도 Jev 비교" 로그를 한 줄 남긴다(JevComparisonLog). 결과는 상담에 쓰지 않는다.
 * <ul>
 *   <li>상담 응답은 Jev를 기다리지 않는다. 비교 로그는 Jev가 끝난 스레드(이미 끝났으면 부른 스레드)에서 남긴다</li>
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
     */
    public record Run(CompletableFuture<JevSide> jev) {
    }

    // 꺼진 그림자 모드. 아무것도 부르지 않는다 (ChatIntentFacade를 그림자 모드 없이 만들 때)
    public static JevIntentShadow off() {
        return new JevIntentShadow(ChatJevProperties.OFF, null, null, null);
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
                    .map(Run::new)
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
     * Jev가 끝나면(이미 끝났으면 바로) 비교 로그를 남긴다. 기다리지 않는다.
     *
     * @param run        start의 결과 (null이면 아무것도 하지 않는다)
     * @param resolution OpenAI 의도 판단과 서버 보정을 거친 최종 결과
     * @param mealkit    밀키트만 보여 줄 추천인지
     */
    public void compareWhenDone(Run run, Long memberId, Long sessionId, int messageLength,
                                ChatIntentResolution resolution, boolean mealkit) {
        if (run == null) {
            return;
        }
        try {
            OpenAiSide openai = OpenAiSide.of(memberId, sessionId, messageLength, resolution, mealkit);
            run.jev().whenComplete((jev, error) -> logComparison(openai, error == null ? jev : JevSide.failed()));
        } catch (RuntimeException e) {
            log.warn("Jev 비교 준비 실패 - memberId: {}, sessionId: {}, 원인: {}", memberId, sessionId, e.getClass().getSimpleName());
        }
    }

    private void logComparison(OpenAiSide openai, JevSide jev) {
        try {
            log.info("{}", JevComparisonLog.line(openai, jev));
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

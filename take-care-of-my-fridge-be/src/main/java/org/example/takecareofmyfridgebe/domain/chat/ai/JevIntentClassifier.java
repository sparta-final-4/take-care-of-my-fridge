package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevAnswers.JevAnswerException;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneCall;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Jev(TypeSafe 분류 모델)로 상담 메시지의 의도 판단 질문에 답을 받는다. 그림자 모드용이라 결과를 상담 흐름에 쓰지 않는다.
 * <ol>
 *   <li>1차(모든 메시지): 의도, 정보를 묻는 말인지, 냉장고 보기 범위 (JevFirstQuestions)</li>
 *   <li>2차(1차 의도가 RECOMMEND·REFINE일 때만): 요리 이름·특징·재료·밀키트, 맛 5개, 분류 (JevSecondQuestions)</li>
 * </ol>
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
    private static final Set<ChatIntent> NEEDS_SECOND = Set.of(ChatIntent.RECOMMEND, ChatIntent.REFINE);

    private final TypeSafeClient typeSafeClient;

    /**
     * @param message    사용자 메시지
     * @param categories 고를 수 있는 레시피 분류 (RecipeService.getClassifications()에서 받은 것)
     * @param followUp   직전 추천 정보 줄 글(ChatIntentFacade.followUp, OpenAI 의도 판단과 같은 것). 첫 질문이면 null
     * @return 판단 결과. 꺼져 있거나, 키가 없거나, 1차가 실패하면 빈 값
     */
    public Optional<JevIntentJudgement> classify(String message, List<String> categories, String followUp) {
        long startedAt = System.nanoTime();
        String state = state(message, followUp);

        Optional<JevIntentJudgement.First> first = typeSafeClient.ask(FIRST, state, JevFirstQuestions.questions())
                .flatMap(call -> read(FIRST, call, JevFirstQuestions::read));
        if (first.isEmpty()) {
            return Optional.empty();
        }

        JevIntentJudgement.Second second = null;
        if (NEEDS_SECOND.contains(first.get().intent())) {
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
        log.info("Jev 의도 판단 - intent: {}, confidence: {}, 메시지 길이: {}, model: {}, "
                        + "1차: {}ms·입력 {}·{}, 2차: {}, 합계: {}ms",
                first.intent(), first.intentConfidence(), messageLength, first.call().model(),
                first.call().latencyMs(), first.call().inputTokens(), first.call().requestId(),
                second == null ? "없음" : second.call().latencyMs() + "ms·입력 " + second.call().inputTokens() + "·" + second.call().requestId(),
                judgement.totalLatencyMs());
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

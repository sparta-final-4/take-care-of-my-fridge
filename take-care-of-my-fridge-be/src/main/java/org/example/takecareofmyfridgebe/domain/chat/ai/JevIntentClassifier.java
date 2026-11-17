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
 * Jev(TypeSafe 분류 모델)로 상담 메시지의 의도 판단 질문에 답을 받는다. 상담 의도는 이 판단으로 정한다 (JevIntentRunner·JevIntentResolver).
 * <ol>
 *   <li>1차(모든 메시지): 의도, 정보를 묻는 말인지, 냉장고 보기 범위, 직전 추천이 있으면 이어지는 말인지 (JevFirstQuestions)</li>
 *   <li>2차(1차 의도가 RECOMMEND·REFINE일 때만): 요리 이름·특징·재료·밀키트, 맛 5개, 요청 종류, 1등 분류 (JevSecondQuestions)</li>
 * </ol>
 * 1차 의도에는 서비스 규칙을 적용한다 (serviceIntent): 직전 추천이 없는데 RECIPE_DETAIL로 답하면 RECOMMEND로 보고(ChatIntentFacade와 같다) 2차도 묻는다.
 * 직전 추천이 있고 추천 + 이어지는 말(follows_previous ≥ {@value #MIN_FOLLOWS_PREVIOUS})이면 REFINE으로 넘긴다(추천 뒤 흐름을 바꾸지 않으려고).
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
    private static final Set<ChatIntent> NEEDS_SECOND = Set.of(ChatIntent.RECOMMEND, ChatIntent.REFINE);
    // 직전 추천이 있어야 뜻이 있는 이어지는 말. 직전 추천이 없으면 새 추천으로 본다
    private static final Set<ChatIntent> FOLLOW_UP_INTENTS = Set.of(ChatIntent.REFINE, ChatIntent.RECIPE_DETAIL);
    // 추천을 직전 추천에 이어지는 말(REFINE)로 볼 follows_previous "예" 확률. 평가로 정함
    static final double MIN_FOLLOWS_PREVIOUS = 0.45;

    private final TypeSafeClient typeSafeClient;

    /**
     * @param message    사용자 메시지
     * @param categories 고를 수 있는 레시피 분류 (RecipeService.getClassifications()에서 받은 것)
     * @param followUp   직전 추천 정보 줄 글(ChatIntentFacade.followUp, OpenAI 의도 판단과 같은 것). 첫 질문이면 null
     * @return 판단 결과. 키가 없거나 1차가 실패하면 빈 값 (부르는 쪽이 OpenAI 결과로 되돌린다)
     */
    public Optional<JevIntentJudgement> classify(String message, List<String> categories, String followUp) {
        long startedAt = System.nanoTime();
        String state = state(message, followUp);
        boolean hasFollowUp = followUp != null && !followUp.isBlank();

        Optional<JevIntentJudgement.First> first = typeSafeClient.ask(FIRST, state, JevFirstQuestions.questions(hasFollowUp))
                .flatMap(call -> read(FIRST, call, answered -> JevFirstQuestions.read(answered, hasFollowUp)));
        if (first.isEmpty()) {
            return Optional.empty();
        }

        JevIntentJudgement.Second second = null;
        if (asksSecond(first.get().intent())) {
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
     * 서비스 규칙을 적용한 의도.
     * <ul>
     *   <li>직전 추천이 없는데 REFINE·RECIPE_DETAIL이면 RECOMMEND다
     *       (ChatIntentFacade: 직전 조건이 없는 다시 추천은 새 추천, 이 상담 추천에 없는 레시피 자세히는 그 요리로 추천)</li>
     *   <li>직전 추천이 있고 추천인데 이어지는 말(followsPrevious ≥ {@value #MIN_FOLLOWS_PREVIOUS})이면 REFINE이다</li>
     * </ul>
     *
     * @param followsPrevious 직전 추천에 이어지는 말일 확률 (follows_previous Noul). 직전 추천이 없어 묻지 않았으면 null
     */
    static ChatIntent serviceIntent(ChatIntent answered, boolean hasFollowUp, Double followsPrevious) {
        if (!hasFollowUp) {
            return FOLLOW_UP_INTENTS.contains(answered) ? ChatIntent.RECOMMEND : answered;
        }
        boolean follows = followsPrevious != null && followsPrevious >= MIN_FOLLOWS_PREVIOUS;
        return answered == ChatIntent.RECOMMEND && follows ? ChatIntent.REFINE : answered;
    }

    // 2차(추천 조건)를 묻는 의도인지 (서비스 규칙을 적용한 의도로 본다)
    static boolean asksSecond(ChatIntent intent) {
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

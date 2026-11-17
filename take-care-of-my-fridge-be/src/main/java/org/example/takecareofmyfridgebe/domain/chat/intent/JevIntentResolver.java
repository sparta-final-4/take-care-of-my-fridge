package org.example.takecareofmyfridgebe.domain.chat.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision.Decision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.facade.SimilarDishFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Jev on 모드: Jev 판단과 OpenAI가 꺼낸 값으로 상담이 쓰는 의도 결과(IntentResult)를 만든다. 결과 모양은 OpenAI 의도 판단과 같아서
 * 그 뒤(ChatIntentFacade의 추천·다시 추천·레시피 자세히·냉장고·사기 처리)는 그대로다. 설계: docs/testing/jev-on-design.md
 * <ul>
 *   <li>의도: Jev. 서비스 규칙은 JevIntentClassifier가 이미 적용했다: 직전 추천이 없는 RECIPE_DETAIL → RECOMMEND,
 *       직전 추천이 있을 때 추천 + 이어지는 말(follows_previous) → REFINE. Jev는 추천을 하나의 의도로 답하고, 이어짐은 추천 안의 항목이다.
 *       추천 뒤 흐름(다시 추천의 직전 조건 합치기)을 바꾸지 않으려고 여기서는 REFINE으로 넘긴다</li>
 *   <li>맛: Jev 원함·피함</li>
 *   <li>OpenAI에서 쓰는 칸: 요리 이름(dishText), 원한 표현(preferenceText), 재료 이름·뺄 재료·재료 꼭 쓰기, 시간,
 *       레시피 자세히 대상·질문 종류, 다시 추천 뺄 조건, 냉장고 범위. 버리는 칸: 의도·맛·분류·키워드</li>
 *   <li>추천·다시 추천은 요청 종류로 검색 조건을 정한다
 *     <ul>
 *       <li>특정 요리: OpenAI 요리 이름과 Jev 1등 분류(없음이면 넣지 않는다). 분류는 거르는 조건이 아니라 점수를 올리는 조건이라
 *           이름이 맞는 레시피와 비슷한 요리 중 같은 분류가 앞에 온다. 요리 이름이 메시지에 그대로 없거나 비었으면
 *           1등 분류가 있을 때 특정 분류로, 없음이면 넓은 요청으로 본다 (맛·재료 칸은 그대로 넘긴다)</li>
 *       <li>특정 분류: Jev 1등 분류. 1등이 없음이면 밀키트를 원하는 말(MealkitWordChecker)이면 없음, 아니면 넓은 요청</li>
 *       <li>넓은 요청: 넓은 요청 요리 이름 5개(SimilarDishFinder.findBroad, 캐시)를 키워드로, 원한 표현을 채워 요리 이름을 말한 추천으로 보지 않는다
 *           (카드가 모자라면 냉장고 후보로 채운다)</li>
 *       <li>없음: 분류·키워드 없이 냉장고 기반</li>
 *     </ul>
 *   </li>
 * </ul>
 * Jev를 쓸지(실패·confidence)는 부르는 쪽(ChatIntentFacade)이 정한다. 서비스 규칙(레시피 자세히 대상, 알레르기 등)은 그 뒤에 따로 거친다.
 * Jev 의도 confidence가 낮아 OpenAI 결과로 되돌릴 때도, OpenAI 의도가 추천 계열이고 Jev 2차 요청 종류가 넓은 요청이면
 * 넓은 요청 경로만 탄다 ({@link #resolveBroadFallback}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JevIntentResolver {

    // 요청 종류로 검색 조건을 정하는 의도
    private static final Set<ChatIntent> RECIPE_INTENTS = Set.of(ChatIntent.RECOMMEND, ChatIntent.REFINE);
    // 넓은 요청 표현을 메시지에서 만들 때 끝에서 지울 문장 부호
    private static final String TRAILING_MARKS = "[?!.~…,]+$";

    private final SimilarDishFinder similarDishFinder;
    private final ChatWordProperties chatWordProperties;

    /**
     * 만든 결과.
     *
     * @param result   상담이 쓰는 의도 결과 (서비스 규칙을 거치기 전)
     * @param decision 비교 로그에 남길 결정 (JEV, 쓴 요청 종류와 값)
     */
    public record Resolved(IntentResult result, JevDecision decision) {
    }

    /**
     * @param input  메시지·회원 (CorrectionInput)
     * @param jev    1차와 (필요하면) 2차까지 성공한 Jev 판단
     * @param openAi sanitize한 OpenAI 결과 (꺼낸 값만 쓴다)
     */
    public Resolved resolve(CorrectionInput input, JevIntentJudgement jev, IntentResult openAi) {
        ChatIntent intent = jev.first().intent();
        JevIntentJudgement.Second second = jev.second();
        List<String> wanted = second == null ? null : tastes(second, TasteStance.WANTED);
        List<String> avoid = second == null ? null : tastes(second, TasteStance.AVOIDED);
        if (!RECIPE_INTENTS.contains(intent) || second == null) {
            return new Resolved(build(intent, openAi, wanted, avoid, null, null, null, openAi.preferenceText()),
                    new JevDecision(Decision.JEV, null, List.of()));
        }

        RequestKind kind = second.requestKind();
        String dish = IntentCorrector.dishInMessage(input.message(), openAi.dishText()) ? openAi.dishText() : null;
        String category = second.category();
        if (kind == RequestKind.DISH && dish == null) {
            kind = category == null ? RequestKind.BROAD : RequestKind.CATEGORY;
        }
        if (kind == RequestKind.CATEGORY && category == null) {
            kind = MealkitWordChecker.check(input.message(), chatWordProperties.mealkit(), chatWordProperties.taste())
                    == MealkitWordChecker.Mode.ONLY ? RequestKind.NONE : RequestKind.BROAD;
        }
        if (kind != second.requestKind()) {
            log.info("상담 Jev 요청 종류 경계 - memberId: {}, sessionId: {}, Jev: {}, 쓴 종류: {}",
                    input.memberId(), input.sessionId(), second.requestKind(), kind);
        }

        return switch (kind) {
            case DISH -> new Resolved(build(intent, openAi, wanted, avoid, category == null ? null : List.of(category), null, dish,
                    openAi.preferenceText()), new JevDecision(Decision.JEV, kind, List.of(dish)));
            case CATEGORY -> new Resolved(build(intent, openAi, wanted, avoid, List.of(category), null, null, openAi.preferenceText()),
                    new JevDecision(Decision.JEV, kind, List.of(category)));
            case BROAD -> broad(input, intent, openAi, wanted, avoid, Decision.JEV);
            case NONE -> new Resolved(build(intent, openAi, wanted, avoid, null, null, null, openAi.preferenceText()),
                    new JevDecision(Decision.JEV, kind, List.of()));
        };
    }

    /**
     * Jev 의도 confidence가 낮아 OpenAI 결과로 되돌릴 때: OpenAI 의도가 추천 계열(RECOMMEND·REFINE)이고 이미 받아 둔 Jev 2차 요청 종류가
     * 넓은 요청이면, 의도·맛 등은 OpenAI 것을 쓰고 검색 조건만 넓은 요청 요리 이름(캐시)으로 바꾼다. OpenAI를 다시 부르지 않는다.
     *
     * @return 넓은 요청 경로를 탄 결과 (decision OPENAI_LOW_CONFIDENCE_JEV_BROAD). 아니면 빈 값 (지금처럼 OpenAI 결과를 쓴다)
     */
    public Optional<Resolved> resolveBroadFallback(CorrectionInput input, JevIntentJudgement jev, IntentResult openAi) {
        JevIntentJudgement.Second second = jev.second();
        if (!RECIPE_INTENTS.contains(openAi.intent()) || second == null || second.requestKind() != RequestKind.BROAD) {
            return Optional.empty();
        }
        return Optional.of(broad(input, openAi.intent(), openAi, openAi.wantedTastes(), openAi.avoidTastes(),
                Decision.OPENAI_LOW_CONFIDENCE_JEV_BROAD));
    }

    // 넓은 요청: 넓은 요청 요리 이름 5개를 키워드로 쓰고, 원한 표현을 채워 요리 이름을 말한 추천으로 보지 않는다 (RecipeCandidateFacade.dishAsked)
    private Resolved broad(CorrectionInput input, ChatIntent intent, IntentResult openAi, List<String> wanted, List<String> avoid,
                           Decision decision) {
        String request = broadRequest(input.message(), openAi.preferenceText());
        List<String> dishes = similarDishFinder.findBroad(input.memberId(), request);
        return new Resolved(build(intent, openAi, wanted, avoid, null, dishes.isEmpty() ? null : dishes, null, request),
                new JevDecision(decision, RequestKind.BROAD, dishes));
    }

    // 넓은 요청 표현: OpenAI 원한 표현, 없으면 메시지에서 끝 문장 부호를 뺀 것. 넓은 요청 요리 이름의 캐시 키가 된다
    static String broadRequest(String message, String preferenceText) {
        if (preferenceText != null && !preferenceText.isBlank()) {
            return preferenceText.strip();
        }
        return message == null ? "" : message.strip().replaceAll(TRAILING_MARKS, "").strip();
    }

    // OpenAI 결과에서 꺼낸 값만 남기고, 의도·맛·분류·키워드·요리 이름·원한 표현은 정한 값으로 채운다
    private static IntentResult build(ChatIntent intent, IntentResult openAi, List<String> wanted, List<String> avoid,
                                      List<String> categories, List<String> keywords, String dishText, String preferenceText) {
        return new IntentResult(intent, wanted, categories, openAi.maxMinutes(), openAi.includeIngredients(),
                openAi.excludeIngredients(), openAi.useSelectedOnly(), keywords, dishText, preferenceText,
                openAi.targetIndex(), openAi.targetName(), openAi.removed(), avoid, openAi.detailQuestion(), openAi.fridgeScope());
    }

    // Jev 맛 답 중 stance인 맛 (Member.Taste 이름, Member.Taste 순서)
    private static List<String> tastes(JevIntentJudgement.Second second, TasteStance stance) {
        return second.tastes().entrySet().stream()
                .filter(entry -> entry.getValue().stance() == stance)
                .map(Map.Entry::getKey)
                .map(Enum::name)
                .toList();
    }
}

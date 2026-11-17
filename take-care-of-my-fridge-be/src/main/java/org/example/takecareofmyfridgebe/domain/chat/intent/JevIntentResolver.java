package org.example.takecareofmyfridgebe.domain.chat.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentClassifier;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision.Decision;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevDecision.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.chat.facade.SimilarDishFinder;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Jev 판단과 OpenAI가 꺼낸 값으로 상담이 쓰는 의도 결과(IntentResult)를 만든다. 결과 모양은 OpenAI 의도 판단과 같아서
 * 그 뒤(ChatIntentFacade의 추천·이어짐·레시피 자세히·냉장고·사기 처리)는 그대로다. 설계: docs/testing/jev-on-design.md
 * <ul>
 *   <li>의도: Jev. 서비스 규칙은 JevIntentClassifier가 이미 적용했다: 직전 추천이 없는 RECIPE_DETAIL → RECOMMEND.
 *       이어짐: 추천이고 직전 추천에 이어지는 말(follows_previous, JevIntentClassifier.follows)이면 예. 추천이 아니면 아니오</li>
 *   <li>맛: Jev 원함·피함</li>
 *   <li>OpenAI에서 쓰는 칸: 요리 이름(dishText), 원한 표현(preferenceText), 재료 이름·뺄 재료·재료 꼭 쓰기, 시간,
 *       레시피 자세히 대상·질문 종류, 이어짐의 뺄 조건, 냉장고 범위. 버리는 칸: 의도·이어짐·맛·분류·키워드</li>
 *   <li>추천(이어짐 포함)은 요청 종류로 검색 조건을 정한다
 *     <ul>
 *       <li>Jev 1등 분류는 확률이 {@value #MIN_CATEGORY_CONFIDENCE} 이상일 때만 쓴다 (아래 "1등 분류"는 모두 이 기준을 넘은 것)</li>
 *       <li>특정 요리: OpenAI 요리 이름과 Jev 1등 분류(없으면 넣지 않는다). 분류는 거르는 조건이 아니라 점수를 올리는 조건이라
 *           이름이 맞는 레시피와 비슷한 요리 중 같은 분류가 앞에 온다. 요리 이름이 메시지에 그대로 없거나 비었으면
 *           메시지에 글자 그대로 있는 첫 OpenAI 키워드를 요리 이름으로 줍는다("○○ 같은 요리"에서 OpenAI가 요리 이름을 비우고 키워드에만 넣음).
 *           요리 이름이 분류 이름과 같으면(죽) 그 분류로 찾는다. 그래도 요리 이름이 없으면 1등 분류가 있을 때 특정 분류로,
 *           없으면 넓은 요청으로 본다 (맛·재료 칸은 그대로 넘긴다)</li>
 *       <li>특정 분류: Jev 1등 분류. 1등 확률이 기준 미만이면 넓은 요청, 1등이 없음이면 밀키트를 원하는 말(MealkitWordChecker)이면 없음,
 *           아니면 넓은 요청</li>
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

    // Jev 1등 분류를 쓸 확률 기준. 평가로 정함 (처음 값 0.7, jev-on-v5)
    static final double MIN_CATEGORY_CONFIDENCE = 0.7;
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
        boolean follows = JevIntentClassifier.follows(jev.first());
        JevIntentJudgement.Second second = jev.second();
        List<String> wanted = second == null ? null : tastes(second, TasteStance.WANTED);
        List<String> avoid = second == null ? null : tastes(second, TasteStance.AVOIDED);
        if (intent != ChatIntent.RECOMMEND || second == null) {
            return new Resolved(build(intent, follows, openAi, wanted, avoid, null, null, null, openAi.preferenceText()),
                    new JevDecision(Decision.JEV, null, List.of()));
        }

        Used used = used(input, second, openAi);
        RequestKind kind = used.kind();
        if (kind != second.requestKind() || used.reason() != Reason.NONE) {
            log.info("상담 Jev 요청 종류 경계 - memberId: {}, sessionId: {}, Jev: {}, 쓴 종류: {}, 까닭: {}, 1등 분류: {}({})",
                    input.memberId(), input.sessionId(), second.requestKind(), kind, used.reason(), second.category(),
                    String.format("%.2f", second.categoryConfidence()));
        }

        return switch (kind) {
            case DISH -> new Resolved(build(intent, follows, openAi, wanted, avoid, used.category() == null ? null : List.of(used.category()),
                    null, used.dish(), openAi.preferenceText()), new JevDecision(Decision.JEV, kind, List.of(used.dish()), used.reason()));
            case CATEGORY -> new Resolved(build(intent, follows, openAi, wanted, avoid, List.of(used.category()), null, null,
                    openAi.preferenceText()), new JevDecision(Decision.JEV, kind, List.of(used.category()), used.reason()));
            case BROAD -> broad(input, intent, follows, openAi, wanted, avoid, Decision.JEV, used.reason());
            case NONE -> new Resolved(build(intent, follows, openAi, wanted, avoid, null, null, null, openAi.preferenceText()),
                    new JevDecision(Decision.JEV, kind, List.of(), used.reason()));
        };
    }

    /**
     * 검색에 쓸 요청 종류와 값.
     *
     * @param dish     요리 이름 (특정 요리일 때만)
     * @param category 분류 (특정 분류일 때, 특정 요리면 함께 넣는 1등 분류. 없으면 null)
     */
    private record Used(RequestKind kind, String dish, String category, Reason reason) {
    }

    // Jev 요청 종류에 경계 규칙을 더해 쓸 종류를 정한다 (클래스 설명의 순서대로)
    private Used used(CorrectionInput input, JevIntentJudgement.Second second, IntentResult openAi) {
        // 1등 분류는 확률이 기준 이상일 때만 쓴다
        String category = second.category() != null && second.categoryConfidence() >= MIN_CATEGORY_CONFIDENCE ? second.category() : null;
        boolean lowCategory = second.category() != null && category == null;
        if (second.requestKind() == RequestKind.DISH) {
            Reason reason = Reason.NONE;
            String dish = IntentCorrector.dishInMessage(input.message(), openAi.dishText()) ? openAi.dishText() : null;
            if (dish == null) {
                dish = keywordInMessage(input.message(), openAi.keywords());
                reason = dish == null ? Reason.NONE : Reason.DISH_FROM_KEYWORD;
            }
            String named = categoryNamed(dish, input.categories());
            if (named != null) {
                return new Used(RequestKind.CATEGORY, null, named, Reason.DISH_IS_CATEGORY);
            }
            if (dish != null) {
                return new Used(RequestKind.DISH, dish, category,
                        reason == Reason.NONE && lowCategory ? Reason.LOW_CATEGORY_CONFIDENCE : reason);
            }
            if (category != null) {
                return new Used(RequestKind.CATEGORY, null, category, Reason.DISH_TO_TOP_CATEGORY);
            }
            return new Used(RequestKind.BROAD, null, null, lowCategory ? Reason.LOW_CATEGORY_CONFIDENCE : Reason.NO_DISH);
        }
        if (second.requestKind() == RequestKind.CATEGORY && category == null) {
            if (lowCategory) {
                return new Used(RequestKind.BROAD, null, null, Reason.LOW_CATEGORY_CONFIDENCE);
            }
            RequestKind kind = MealkitWordChecker.check(input.message(), chatWordProperties.mealkit(), chatWordProperties.taste())
                    == MealkitWordChecker.Mode.ONLY ? RequestKind.NONE : RequestKind.BROAD;
            return new Used(kind, null, null, Reason.NO_TOP_CATEGORY);
        }
        return new Used(second.requestKind(), null, category, Reason.NONE);
    }

    // OpenAI 키워드 중 메시지에 글자 그대로(띄어쓰기 무시) 있는 첫 키워드. OpenAI가 덧붙인 비슷한 요리는 메시지에 없어 걸리지 않는다
    static String keywordInMessage(String message, List<String> keywords) {
        if (keywords == null) {
            return null;
        }
        return keywords.stream().filter(keyword -> IntentCorrector.dishInMessage(message, keyword)).findFirst().orElse(null);
    }

    // 요리 이름이 분류 목록(Jev 분류 선택지와 같은 DB 목록)의 이름과 같으면 그 분류 이름 ("죽" → 죽). 아니면 null
    static String categoryNamed(String dish, List<String> categories) {
        if (dish == null || categories == null) {
            return null;
        }
        String compact = ChatTexts.removeWhitespace(dish);
        return categories.stream().filter(category -> ChatTexts.removeWhitespace(category).equals(compact)).findFirst().orElse(null);
    }

    /**
     * Jev 의도 confidence가 낮아 OpenAI 결과로 되돌릴 때: OpenAI 의도가 추천(이어짐 포함, 이어짐은 OpenAI 값)이고 이미 받아 둔 Jev 2차 요청 종류가
     * 넓은 요청이면, 의도·맛 등은 OpenAI 것을 쓰고 검색 조건만 넓은 요청 요리 이름(캐시)으로 바꾼다. OpenAI를 다시 부르지 않는다.
     *
     * @return 넓은 요청 경로를 탄 결과 (decision OPENAI_LOW_CONFIDENCE_JEV_BROAD). 아니면 빈 값 (지금처럼 OpenAI 결과를 쓴다)
     */
    public Optional<Resolved> resolveBroadFallback(CorrectionInput input, JevIntentJudgement jev, IntentResult openAi) {
        JevIntentJudgement.Second second = jev.second();
        if (openAi.intent() != ChatIntent.RECOMMEND || second == null || second.requestKind() != RequestKind.BROAD) {
            return Optional.empty();
        }
        return Optional.of(broad(input, openAi.intent(), openAi.follows(), openAi, openAi.wantedTastes(), openAi.avoidTastes(),
                Decision.OPENAI_LOW_CONFIDENCE_JEV_BROAD, Reason.NONE));
    }

    // 넓은 요청: 넓은 요청 요리 이름 5개를 키워드로 쓰고, 원한 표현을 채워 요리 이름을 말한 추천으로 보지 않는다 (RecipeCandidateFacade.dishAsked)
    private Resolved broad(CorrectionInput input, ChatIntent intent, boolean follows, IntentResult openAi, List<String> wanted,
                           List<String> avoid, Decision decision, Reason reason) {
        String request = broadRequest(input.message(), openAi.preferenceText());
        List<String> dishes = similarDishFinder.findBroad(input.memberId(), request);
        return new Resolved(build(intent, follows, openAi, wanted, avoid, null, dishes.isEmpty() ? null : dishes, null, request),
                new JevDecision(decision, RequestKind.BROAD, dishes, reason));
    }

    // 넓은 요청 표현: OpenAI 원한 표현, 없으면 메시지에서 끝 문장 부호를 뺀 것. 넓은 요청 요리 이름의 캐시 키가 된다
    static String broadRequest(String message, String preferenceText) {
        if (preferenceText != null && !preferenceText.isBlank()) {
            return preferenceText.strip();
        }
        return message == null ? "" : message.strip().replaceAll(TRAILING_MARKS, "").strip();
    }

    // OpenAI 결과에서 꺼낸 값만 남기고, 의도·이어짐·맛·분류·키워드·요리 이름·원한 표현은 정한 값으로 채운다
    private static IntentResult build(ChatIntent intent, boolean follows, IntentResult openAi, List<String> wanted, List<String> avoid,
                                      List<String> categories, List<String> keywords, String dishText, String preferenceText) {
        return new IntentResult(intent, wanted, categories, openAi.maxMinutes(), openAi.includeIngredients(),
                openAi.excludeIngredients(), openAi.useSelectedOnly(), keywords, dishText, preferenceText,
                openAi.targetIndex(), openAi.targetName(), openAi.removed(), avoid, openAi.detailQuestion(), openAi.fridgeScope(),
                follows);
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

package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevAnswers.Option;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.RequestKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneCall;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneQuestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jev 2차 질문(1차 의도가 RECOMMEND일 때): 요리 이름·특징·재료·밀키트를 말했는지, 맛 5개, 요청 종류, 1등 분류.
 * 단어 규칙 없이 뜻만 묻는다. 서버는 단어 목록으로 Jev 답을 고치지 않는다.
 * 분류는 고를 수 있는 분류 + "없음" 중 하나를 고르는 Choice다. 1등 하나만 쓰고, 요청 종류가 특정 분류일 때만 의미가 있다.
 * (분류마다 "어울리나"를 묻는 방식은 거의 모든 분류에 "예"라고 해서 버렸다: docs/testing/eval-results/jev-v2)
 */
final class JevSecondQuestions {

    static final String DISH_NAMED = "dish_named";
    static final String FEATURE_NAMED = "feature_named";
    static final String INGREDIENT_NAMED = "ingredient_named";
    static final String MEALKIT_WANTED = "mealkit_wanted";
    static final String TASTE_PREFIX = "taste_";
    static final String REQUEST_KIND = "request_kind";
    static final String CATEGORY = "category";
    // 분류 Choice에서 맞는 분류가 없다는 선택지
    static final String NO_CATEGORY = "없음";

    static final List<Option<RequestKind>> REQUEST_KINDS = List.of(
            new Option<>("특정 요리", RequestKind.DISH, "요리 하나의 이름을 말했다"),
            new Option<>("특정 분류", RequestKind.CATEGORY, "요리의 종류를 하나 말했다(찌개, 디저트처럼)"),
            new Option<>("넓은 요청", RequestKind.BROAD, "상황이나 쓰임새, 여러 종류에 걸친 말을 했다(술안주, 반찬, 국물, 간단한 한 끼처럼). "
                    + "말한 종류가 여러 분류에 걸치면 넓은 요청이다"),
            new Option<>("없음", RequestKind.NONE, "요리 종류나 이름을 말하지 않았다"));

    private JevSecondQuestions() {
    }

    static Map<String, SystemOneQuestion> questions(List<String> categories) {
        Map<String, SystemOneQuestion> questions = new LinkedHashMap<>();
        questions.put(DISH_NAMED, SystemOneQuestion.noul("특정 요리 이름을 말했나"));
        questions.put(FEATURE_NAMED, SystemOneQuestion.noul("요리의 특징(차가운, 바삭한, 국물 등)을 말했나"));
        questions.put(INGREDIENT_NAMED, SystemOneQuestion.noul("재료 이름을 말했나"));
        questions.put(MEALKIT_WANTED, SystemOneQuestion.noul("밀키트를 원하나"));
        for (Member.Taste taste : Member.Taste.values()) {
            questions.put(tasteName(taste), JevAnswers.choiceQuestion(
                    "사용자가 " + tasteLabel(taste) + "(" + tasteDescription(taste) + ")을 원하나, 피하고 싶어 하나, 말하지 않았나?",
                    tasteStances(taste)));
        }
        questions.put(REQUEST_KIND, JevAnswers.choiceQuestion("사용자는 원하는 요리를 어떻게 말했나?", REQUEST_KINDS));
        questions.put(CATEGORY, JevAnswers.choiceQuestion("사용자가 원하는 요리는 어느 분류인가?", categoryOptions(categories)));
        return Collections.unmodifiableMap(questions);
    }

    // 고를 수 있는 분류 + 없음. 선택지 이름과 값은 분류 이름이다
    static List<Option<String>> categoryOptions(List<String> categories) {
        List<Option<String>> options = new ArrayList<>();
        categories.forEach(category -> options.add(new Option<>(category, category, category + " 분류의 요리")));
        options.add(new Option<>(NO_CATEGORY, NO_CATEGORY, "요리 종류를 말하지 않았거나 맞는 분류가 없다"));
        return options;
    }

    static JevIntentJudgement.Second read(SystemOneCall call, List<String> categories) {
        JevAnswers answers = new JevAnswers(call);
        Map<Member.Taste, TasteAnswer> tastes = new EnumMap<>(Member.Taste.class);
        for (Member.Taste taste : Member.Taste.values()) {
            JevAnswers.Choice<TasteStance> stance = answers.choice(tasteName(taste), tasteStances(taste));
            tastes.put(taste, new TasteAnswer(stance.value(), stance.confidence()));
        }
        JevAnswers.Choice<RequestKind> requestKind = answers.choice(REQUEST_KIND, REQUEST_KINDS);
        JevAnswers.Choice<String> category = answers.choice(CATEGORY, categoryOptions(categories));
        Map.Entry<String, Double> runnerUp = runnerUp(category);
        return new JevIntentJudgement.Second(
                answers.noul(DISH_NAMED), answers.noul(FEATURE_NAMED),
                answers.noul(INGREDIENT_NAMED), answers.noul(MEALKIT_WANTED),
                Collections.unmodifiableMap(tastes),
                requestKind.value(), requestKind.confidence(),
                NO_CATEGORY.equals(category.value()) ? null : category.value(), category.confidence(),
                runnerUp == null ? null : runnerUp.getKey(), runnerUp == null ? null : runnerUp.getValue(),
                answers.call());
    }

    // 분류 Choice의 2등 (1등을 뺀 가장 높은 확률). 선택지가 하나뿐이면 null
    private static Map.Entry<String, Double> runnerUp(JevAnswers.Choice<String> category) {
        return category.probabilities().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(category.value()))
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    static String tasteName(Member.Taste taste) {
        return TASTE_PREFIX + taste.name().toLowerCase(Locale.ROOT);
    }

    // 선택지 이름(원함·피함·언급 없음)은 맛마다 같고 설명에 그 맛 이름을 넣는다.
    // "매운 거 말고"에 순한맛까지 피함으로 답하지 않게, 언급 없음 설명에 다른 맛 말은 이 맛과 상관없다고 적는다
    static List<Option<TasteStance>> tasteStances(Member.Taste taste) {
        String label = tasteLabel(taste);
        return List.of(
                new Option<>("원함", TasteStance.WANTED, label + "을 원한다"),
                new Option<>("피함", TasteStance.AVOIDED, label + "을 피하고 싶다"),
                new Option<>("언급 없음", TasteStance.NOT_MENTIONED,
                        label + "에 대해 말하지 않았다. 다른 맛을 원하거나 피하는 말은 " + label + "과 상관없다"));
    }

    // 맛 이름. 모두 받침이 있어 뒤에 "을"·"과"를 붙인다
    private static String tasteLabel(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짠맛";
            case SWEET -> "단맛";
            case SPICY -> "매운맛";
            case UMAMI -> "감칠맛";
            case MILD -> "순한맛";
        };
    }

    // chat-intent.txt wantedTastes·avoidTastes의 맛 설명을 줄인 것
    private static String tasteDescription(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짭짤한 맛";
            case SWEET -> "달달한 맛";
            case SPICY -> "얼큰하고 칼칼한 맛";
            case UMAMI -> "고소한 맛";
            case MILD -> "담백한 맛";
        };
    }
}

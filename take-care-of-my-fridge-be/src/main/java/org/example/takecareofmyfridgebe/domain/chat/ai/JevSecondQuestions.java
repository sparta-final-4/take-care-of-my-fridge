package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.ai.JevAnswers.Option;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteAnswer;
import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement.TasteStance;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneCall;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneQuestion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jev 2차 질문(1차 의도가 RECOMMEND·REFINE일 때): 요리 이름·특징·재료·밀키트를 말했는지, 맛 5개, 분류마다 Noul.
 * 맛·분류는 단어 규칙 없이 뜻만 묻는다. 서버는 단어 목록으로 Jev 답을 고치지 않는다.
 * 분류는 Choice 하나로 물으면 확률이 한 분류에 몰려서(jev-api-notes.md) 분류마다 "어울리나"를 따로 묻고,
 * 몇 개를 고를지는 JevIntentJudgement.CategoryRule이 정한다.
 * 질문 이름에 한글을 쓰지 않으려고 분류는 순번(category_0, category_1, …)으로 묻고, 결과는 분류 이름으로 되돌린다.
 */
final class JevSecondQuestions {

    static final String DISH_NAMED = "dish_named";
    static final String FEATURE_NAMED = "feature_named";
    static final String INGREDIENT_NAMED = "ingredient_named";
    static final String MEALKIT_WANTED = "mealkit_wanted";
    static final String TASTE_PREFIX = "taste_";
    static final String CATEGORY_PREFIX = "category_";

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
        for (int i = 0; i < categories.size(); i++) {
            questions.put(CATEGORY_PREFIX + i, SystemOneQuestion.noul("이 요청에 '" + categories.get(i) + "' 요리가 어울리나?"));
        }
        return Collections.unmodifiableMap(questions);
    }

    static JevIntentJudgement.Second read(SystemOneCall call, List<String> categories) {
        JevAnswers answers = new JevAnswers(call);
        Map<Member.Taste, TasteAnswer> tastes = new EnumMap<>(Member.Taste.class);
        for (Member.Taste taste : Member.Taste.values()) {
            JevAnswers.Choice<TasteStance> stance = answers.choice(tasteName(taste), tasteStances(taste));
            tastes.put(taste, new TasteAnswer(stance.value(), stance.confidence()));
        }
        Map<String, Double> categoryProbabilities = new LinkedHashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            categoryProbabilities.put(categories.get(i), answers.noul(CATEGORY_PREFIX + i));
        }
        return new JevIntentJudgement.Second(
                answers.noul(DISH_NAMED), answers.noul(FEATURE_NAMED),
                answers.noul(INGREDIENT_NAMED), answers.noul(MEALKIT_WANTED),
                Collections.unmodifiableMap(tastes), Collections.unmodifiableMap(categoryProbabilities),
                answers.call());
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

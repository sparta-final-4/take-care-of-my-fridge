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
 * 분류는 Choice 하나로 물으면 확률이 한 분류에 몰려서(jev-api-notes.md) 분류마다 따로 묻는다.
 * 질문 이름에 한글을 쓰지 않으려고 분류는 순번(category_0, category_1, …)으로 묻고, 결과는 분류 이름으로 되돌린다.
 */
final class JevSecondQuestions {

    static final String DISH_NAMED = "dish_named";
    static final String FEATURE_NAMED = "feature_named";
    static final String INGREDIENT_NAMED = "ingredient_named";
    static final String MEALKIT_WANTED = "mealkit_wanted";
    static final String TASTE_PREFIX = "taste_";
    static final String CATEGORY_PREFIX = "category_";

    static final List<Option<TasteStance>> TASTE_STANCES = List.of(
            new Option<>("원함", TasteStance.WANTED, "그 맛을 원한다"),
            new Option<>("피함", TasteStance.AVOIDED, "그 맛을 빼거나 덜하길 원한다"),
            new Option<>("언급 없음", TasteStance.NOT_MENTIONED, "그 맛을 말하지 않았다"));

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
                    tasteWords(taste) + "에 대해 어떻게 말했나? \"말고\", \"안\", \"덜\", \"싫어\"가 붙은 맛은 피함이다",
                    TASTE_STANCES));
        }
        for (int i = 0; i < categories.size(); i++) {
            questions.put(CATEGORY_PREFIX + i, SystemOneQuestion.noul("원하는 요리 분류가 '" + categories.get(i) + "'인가"));
        }
        return Collections.unmodifiableMap(questions);
    }

    static JevIntentJudgement.Second read(SystemOneCall call, List<String> categories) {
        JevAnswers answers = new JevAnswers(call);
        Map<Member.Taste, TasteAnswer> tastes = new EnumMap<>(Member.Taste.class);
        for (Member.Taste taste : Member.Taste.values()) {
            JevAnswers.Choice<TasteStance> stance = answers.choice(tasteName(taste), TASTE_STANCES);
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

    // chat-intent.txt의 맛 설명 (wantedTastes·avoidTastes)
    private static String tasteWords(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짠맛(짠맛·짭짤한)";
            case SWEET -> "단맛(단맛·달달한)";
            case SPICY -> "매운맛(매운맛·얼큰한·칼칼한)";
            case UMAMI -> "감칠맛(감칠맛·고소한)";
            case MILD -> "순한맛(순한맛·담백한)";
        };
    }
}

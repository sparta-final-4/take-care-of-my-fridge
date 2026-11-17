package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담이 쓰는 의도와 추천 조건. Jev 판단(의도·이어짐·맛·분류·요청 종류)과 OpenAI가 꺼낸 값(IntentExtraction, 정리한 것)으로
 * JevIntentResolver가 만들고, 서비스 규칙(IntentCorrector)이 고친다. 말하지 않은 조건은 null이고 빈 목록도 말하지 않은 것과 같게 다룬다.
 *
 * @param intent             RECOMMEND / GENERAL / RECIPE_DETAIL / FRIDGE / SHOP / OFF_TOPIC / BLOCKED / SELF_HARM (ChatIntent, Jev)
 * @param wantedTastes       원하는 맛 (Member.Taste 이름, Jev)
 * @param moodCategories     원하는 레시피 분류 (recipes.category 값, Jev 1등 분류)
 * @param maxMinutes         최대 조리 시간(분). 말하지 않았으면 null
 * @param includeIngredients 꼭 넣고 싶은 재료 이름 (말한 그대로)
 * @param excludeIngredients 빼고 싶은 재료 이름 (말한 그대로)
 * @param useSelectedOnly    고른·말한 재료를 꼭 쓰는 레시피만 원하는지
 * @param keywords           특징만 말했을 때("차가운 요리") 그 특징에 맞는 요리 이름 (예: 냉면, 막국수). 요리 이름을 말했으면 그 이름만.
 *                           예전 기록에는 요리 이름과 비슷한 요리도 들어 있다. 비슷한 요리는 후보 검색 단계에서 따로 묻는다(SimilarDishFinder)
 * @param dishText           사용자가 말한 요리 이름 그대로 (예: "냉면"). 레시피 이름 비교와 카드 이유에 쓴다.
 *                           AI가 뽑고(분류·특징이면 빈 문자열), 서버는 메시지에 그대로 있는지만 확인한다 (IntentCorrector.keepSpokenDishOnly).
 *                           Jev가 특정 요리로 봤는데 비었으면 메시지에 있는 첫 키워드를 쓴다 (JevIntentResolver)
 * @param preferenceText     사용자가 원한 특징·요리 표현 그대로 (예: "차가운 요리", "냉면 같은 면 요리"). 요청에 못 맞췄을 때 안내에 쓴다
 * @param targetIndex        RECIPE_DETAIL일 때 직전 추천 카드 번호(1부터). 모르면 0
 * @param targetName         RECIPE_DETAIL일 때 말한 레시피 이름 (예: "깐풍기"). 없으면 빈 문자열
 * @param removed            이어짐일 때 지우라고 한 조건 종류 (RefineCondition 이름, "시간 상관없이" → MAX_MINUTES). 없으면 빈 목록
 * @param avoidTastes        피할 맛 (Member.Taste 이름, Jev, "매운 거 말고" → SPICY). 말하지 않았으면 빈 목록
 * @param detailQuestion     RECIPE_DETAIL일 때 질문 종류 (DetailQuestion 이름: HOW_TO, TIME, INGREDIENTS, TIP, OTHER). 다른 의도면 빈 값
 * @param fridgeScope        FRIDGE일 때 보여 줄 범위 (FridgeScope 이름: ALL, MEALKIT, IMMINENT). 다른 의도면 ALL
 * @param followsPrevious    이어짐(Jev): 추천일 때 [직전 추천]의 조건을 이어받아 일부만 바꾸는 추천인지. 직전 추천이 없거나 추천이 아니면 false.
 *                           최종 값은 follows()로 읽는다 (추천일 때만 뜻이 있다). 직전 추천이 없으면 아니오로 두는 것은 서비스 규칙이다 (IntentCorrector)
 */
public record IntentResult(
        ChatIntent intent,
        List<String> wantedTastes,
        List<String> moodCategories,
        Integer maxMinutes,
        List<String> includeIngredients,
        List<String> excludeIngredients,
        Boolean useSelectedOnly,
        List<String> keywords,
        String dishText,
        String preferenceText,
        Integer targetIndex,
        String targetName,
        List<String> removed,
        List<String> avoidTastes,
        String detailQuestion,
        String fridgeScope,
        Boolean followsPrevious
) {

    // 레시피 검색에 쓸 요리 이름 키워드 최대 개수
    public static final int MAX_KEYWORDS = 6;

    private static final Set<String> TASTE_NAMES = Arrays.stream(Member.Taste.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> QUESTION_NAMES = Arrays.stream(DetailQuestion.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> CONDITION_NAMES = Arrays.stream(RefineCondition.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> SCOPE_NAMES = Arrays.stream(FridgeScope.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public IntentResult(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                        List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                        List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                        List<String> removed, List<String> avoidTastes, String detailQuestion, String fridgeScope) {
        this(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion, fridgeScope, null);
    }

    public IntentResult(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                        List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                        List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                        List<String> removed, List<String> avoidTastes, String detailQuestion) {
        this(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion, null);
    }

    public IntentResult(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                        List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                        List<String> keywords, String dishText, String preferenceText) {
        this(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, null, null);
    }

    public IntentResult(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                        List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                        List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName) {
        this(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, null);
    }

    public IntentResult(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                        List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                        List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                        List<String> removed) {
        this(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, null);
    }

    public IntentResult(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                        List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                        List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                        List<String> removed, List<String> avoidTastes) {
        this(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, null);
    }

    // 서버가 냉장고 보기·사기로 바꾼 결과. 범위와 말한 재료를 서버가 정한 값으로 바꾼다
    public IntentResult withLookup(ChatIntent newIntent, FridgeScope scope, List<String> names) {
        return new IntentResult(newIntent, wantedTastes, moodCategories, maxMinutes, names, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion,
                scope.name(), false);
    }

    // 의도만 있고 조건은 하나도 말하지 않은 결과
    public static IntentResult general() {
        return new IntentResult(ChatIntent.GENERAL, null, null, null, null, null, null, null, null, null);
    }

    // 분류·키워드·요리 이름·뺄 조건을 바꾼 결과 (요리 이름 확인, Jev 판단으로 만든 결과)
    public IntentResult withCategories(List<String> categories, List<String> keywords, String dishText, List<String> removed) {
        return new IntentResult(intent, wantedTastes, categories, maxMinutes, includeIngredients, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion,
                fridgeScope, followsPrevious);
    }

    // 원하는 맛·피할 맛을 바꾼 결과 (말한 맛만 남기기)
    public IntentResult withTastes(List<String> wanted, List<String> avoid) {
        return new IntentResult(intent, wanted, moodCategories, maxMinutes, includeIngredients, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, removed, avoid, detailQuestion,
                fridgeScope, followsPrevious);
    }

    // 서버가 바로잡은 뺄 조건
    public IntentResult withRemoved(List<String> newRemoved) {
        return new IntentResult(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, newRemoved, avoidTastes, detailQuestion,
                fridgeScope, followsPrevious);
    }

    // 못 찾은 레시피 자세히 대상으로 채운 추천 (재료 이름, 요리 이름 키워드, 요리 이름)
    public IntentResult withNamed(List<String> includes, List<String> namedKeywords, String dish) {
        return new IntentResult(ChatIntent.RECOMMEND, wantedTastes, moodCategories, maxMinutes, includes, excludeIngredients,
                useSelectedOnly, namedKeywords, dish, preferenceText, targetIndex, targetName, removed, avoidTastes,
                detailQuestion, fridgeScope, false);
    }

    // 이어짐을 바꾼 결과 (Jev 판단으로 정한 이어짐, 직전 추천이 없을 때 아니오로 두기)
    public IntentResult withFollows(boolean follows) {
        return new IntentResult(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion,
                fridgeScope, follows);
    }

    // 직전 추천의 조건을 이어받는 추천인지 (추천이고 이어짐이 예). 추천이 아니면 이어짐 값이 있어도 아니오다
    public boolean follows() {
        return intent == ChatIntent.RECOMMEND && Boolean.TRUE.equals(followsPrevious);
    }

    /**
     * 레시피 검색에 쓸 키워드: [요리 이름] + AI 키워드 중 남은 것 (순서 유지, 띄어쓰기만 다른 이름은 한 번, 최대 {@value #MAX_KEYWORDS}개).
     * 요리 이름이 없으면 AI 키워드 그대로다 (특징만 말한 추천). 요리 이름이 키워드 맨 앞에 오게 해 "김피탕"과 "김피탕 같은 요리 추천해줘"가
     * 같은 요리 이름을 받으면 같은 키워드가 되게 한다.
     */
    public List<String> searchKeywords() {
        if (dishText == null) {
            return keywords;
        }
        Set<String> seen = new HashSet<>();
        return Stream.concat(Stream.of(dishText), keywords == null ? Stream.empty() : keywords.stream())
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .filter(name -> seen.add(ChatTexts.removeWhitespace(name)))
                .limit(MAX_KEYWORDS)
                .toList();
    }

    // 냉장고 보기 범위 (없거나 모르는 값이면 ALL)
    public FridgeScope scope() {
        return fridgeScope == null || !SCOPE_NAMES.contains(fridgeScope) ? FridgeScope.ALL : FridgeScope.valueOf(fridgeScope);
    }

    // 빼라고 한 조건 종류 (없으면 빈 집합)
    public Set<RefineCondition> removedConditions() {
        if (removed == null) {
            return Set.of();
        }
        return removed.stream()
                .filter(CONDITION_NAMES::contains)
                .map(RefineCondition::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    // 레시피 자세히 질문 종류 (없으면 null)
    public DetailQuestion question() {
        return detailQuestion == null || !QUESTION_NAMES.contains(detailQuestion) ? null : DetailQuestion.valueOf(detailQuestion);
    }

    // 피할 맛을 Member.Taste로 바꾼다 (말하지 않았으면 빈 목록)
    public List<Member.Taste> avoidedTastes() {
        if (avoidTastes == null) {
            return List.of();
        }
        return avoidTastes.stream()
                .filter(TASTE_NAMES::contains)
                .map(Member.Taste::valueOf)
                .toList();
    }

    // 맛 이름을 Member.Taste로 바꾼다 (말하지 않았으면 빈 목록)
    public List<Member.Taste> tastes() {
        if (wantedTastes == null) {
            return List.of();
        }
        return wantedTastes.stream()
                .filter(TASTE_NAMES::contains)
                .map(Member.Taste::valueOf)
                .toList();
    }
}

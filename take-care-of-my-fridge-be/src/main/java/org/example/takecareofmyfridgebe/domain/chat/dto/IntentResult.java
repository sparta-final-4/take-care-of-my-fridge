package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 상담 메시지에서 AI가 뽑은 의도와 추천 조건.
 * 응답 JSON 스키마가 null을 허용하지 않아, 사용자가 말하지 않은 조건은 시간 0·빈 목록·false로 온다 (프롬프트로 정함).
 * 0은 sanitize에서 null로 바꾸고, 빈 목록은 말하지 않은 것과 같게 다룬다. 테스트·스텁에서는 null로 올 수도 있다.
 * AI 응답을 그대로 받는 모양이라 값이 틀릴 수 있으므로 sanitize로 정리해서 쓴다.
 *
 * @param intent             RECOMMEND / GENERAL / REFINE / RECIPE_DETAIL / OFF_TOPIC / BLOCKED / SELF_HARM (ChatIntent)
 * @param wantedTastes       원하는 맛 (Member.Taste 이름)
 * @param moodCategories     원하는 레시피 분류 (recipes.category 값)
 * @param maxMinutes         최대 조리 시간(분). 말하지 않았으면 0
 * @param includeIngredients 꼭 넣고 싶은 재료 이름 (말한 그대로)
 * @param excludeIngredients 빼고 싶은 재료 이름 (말한 그대로)
 * @param useSelectedOnly    고른·말한 재료를 꼭 쓰는 레시피만 원하는지
 * @param keywords           레시피 이름에 나올 만한 구체적인 요리 이름 (예: 냉면, 막국수). 특징("차가운")도 요리 이름으로 바꾼 것
 * @param dishText           사용자가 말한 요리 이름 (예: "냉면"). 레시피 이름 비교와 카드 이유에 쓴다
 * @param preferenceText     사용자가 원한 특징·요리 표현 그대로 (예: "차가운 요리", "냉면 같은 면 요리"). 요청에 못 맞췄을 때 안내에 쓴다
 * @param targetIndex        RECIPE_DETAIL일 때 직전 추천 카드 번호(1부터). 모르면 0
 * @param targetName         RECIPE_DETAIL일 때 말한 레시피 이름 (예: "깐풍기"). 없으면 빈 문자열
 * @param removed            REFINE일 때 지우라고 한 조건 종류 (RefineCondition 이름, "시간 상관없이" → MAX_MINUTES). 없으면 빈 목록
 * @param avoidTastes        피할 맛 (Member.Taste 이름, "매운 거 말고" → SPICY). 말하지 않았으면 빈 목록
 * @param detailQuestion     RECIPE_DETAIL일 때 질문 종류 (DetailQuestion 이름: HOW_TO, TIME, INGREDIENTS, TIP, OTHER). 다른 의도면 빈 값
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
        String detailQuestion
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

    // 의도만 있고 조건은 하나도 말하지 않은 결과
    public static IntentResult general() {
        return new IntentResult(ChatIntent.GENERAL, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 값을 정리한다. 말하지 않은 칸(null)은 null로 둔다.
     * 의도가 없으면 GENERAL, 맛은 Member.Taste에 있는 값만, 분류는 allowedCategories에 있는 값만 남긴다.
     * 조리 시간이 0 이하면 null, 재료 이름·키워드는 앞뒤 공백을 지우고 빈 이름을 뺀다. 목록은 중복을 빼고, 키워드는 6개까지.
     * 요리 이름(dishText)·원한 표현(preferenceText)·대상 레시피 이름(targetName)이 비어 있으면 null, 대상 카드 번호가 0 이하면 null.
     * 뺄 조건(removed)은 RefineCondition에 있는 이름만 남긴다 (소문자는 대문자로).
     *
     * @param allowedCategories 고를 수 있는 레시피 분류
     */
    public IntentResult sanitize(Collection<String> allowedCategories) {
        Set<String> categories = Set.copyOf(allowedCategories);
        return new IntentResult(
                intent == null ? ChatIntent.GENERAL : intent,
                cleanNames(wantedTastes, name -> name.toUpperCase(Locale.ROOT), TASTE_NAMES),
                cleanNames(moodCategories, name -> name, categories),
                maxMinutes == null || maxMinutes <= 0 ? null : maxMinutes,
                cleanNames(includeIngredients, name -> name, null),
                cleanNames(excludeIngredients, name -> name, null),
                useSelectedOnly,
                keywords == null ? null : cleanNames(keywords, name -> name, null).stream().limit(MAX_KEYWORDS).toList(),
                dishText == null || dishText.isBlank() ? null : dishText.strip(),
                preferenceText == null || preferenceText.isBlank() ? null : preferenceText.strip(),
                targetIndex == null || targetIndex <= 0 ? null : targetIndex,
                targetName == null || targetName.isBlank() ? null : targetName.strip(),
                cleanNames(removed, name -> name.toUpperCase(Locale.ROOT), CONDITION_NAMES),
                cleanNames(avoidTastes, name -> name.toUpperCase(Locale.ROOT), TASTE_NAMES),
                detailQuestion == null || !QUESTION_NAMES.contains(detailQuestion.strip().toUpperCase(Locale.ROOT))
                        ? null
                        : detailQuestion.strip().toUpperCase(Locale.ROOT)
        );
    }

    // 서버가 메시지의 분류 단어로 분류·키워드·요리 이름·뺄 조건을 바로잡은 결과 (CategoryWordChecker)
    public IntentResult withCategories(List<String> categories, List<String> keywords, String dishText, List<String> removed) {
        return new IntentResult(intent, wantedTastes, categories, maxMinutes, includeIngredients, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion);
    }

    // 빼라고 한 조건 종류. sanitize한 뒤에 쓴다 (없으면 빈 집합)
    public Set<RefineCondition> removedConditions() {
        if (removed == null) {
            return Set.of();
        }
        return removed.stream()
                .filter(CONDITION_NAMES::contains)
                .map(RefineCondition::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    // 레시피 자세히 질문 종류. sanitize한 뒤에 쓴다 (없으면 null)
    public DetailQuestion question() {
        return detailQuestion == null || !QUESTION_NAMES.contains(detailQuestion) ? null : DetailQuestion.valueOf(detailQuestion);
    }

    // 피할 맛을 Member.Taste로 바꾼다. sanitize한 뒤에 쓴다 (말하지 않았으면 빈 목록)
    public List<Member.Taste> avoidedTastes() {
        if (avoidTastes == null) {
            return List.of();
        }
        return avoidTastes.stream()
                .filter(TASTE_NAMES::contains)
                .map(Member.Taste::valueOf)
                .toList();
    }

    // 맛 이름을 Member.Taste로 바꾼다. sanitize한 뒤에 쓴다 (말하지 않았으면 빈 목록)
    public List<Member.Taste> tastes() {
        if (wantedTastes == null) {
            return List.of();
        }
        return wantedTastes.stream()
                .filter(TASTE_NAMES::contains)
                .map(Member.Taste::valueOf)
                .toList();
    }

    private static List<String> cleanNames(
            List<String> names,
            UnaryOperator<String> transform,
            Set<String> allowed
    ) {
        if (names == null) {
            return null;
        }
        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(transform)
                .filter(name -> allowed == null || allowed.contains(name))
                .distinct()
                .toList();
    }
}

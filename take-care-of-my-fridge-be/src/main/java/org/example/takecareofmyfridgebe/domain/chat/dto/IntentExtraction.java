package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * OpenAI가 상담 메시지에서 꺼낸 값 (의도는 정하지 않는다. 의도·이어짐·맛·분류는 Jev가 정한다).
 * 응답 JSON 스키마가 null을 허용하지 않아, 사용자가 말하지 않은 값은 시간 0·빈 목록·false·빈 문자열로 온다 (프롬프트로 정함).
 * AI 응답을 그대로 받는 모양이라 값이 틀릴 수 있으므로 sanitize로 정리해서 쓴다. 테스트·스텁에서는 null로 올 수도 있다.
 * 상담이 쓰는 결과(IntentResult)는 Jev 판단과 이 값으로 JevIntentResolver가 만든다.
 *
 * @param maxMinutes         최대 조리 시간(분). 말하지 않았으면 0
 * @param includeIngredients 꼭 넣고 싶은 재료 이름 (말한 그대로). 냉장고 보기·사기면 찾거나 살 재료·상품 이름
 * @param excludeIngredients 빼고 싶은 재료 이름 (말한 그대로)
 * @param useSelectedOnly    고른·말한 재료를 꼭 쓰는 레시피만 원하는지
 * @param keywords           특징만 말했을 때 그 특징에 맞는 요리 이름. 요리 이름을 말했으면 그 이름만.
 *                           Jev가 특정 요리로 봤는데 요리 이름이 비었으면 메시지에 그대로 있는 첫 키워드를 요리 이름으로 쓴다 (JevIntentResolver)
 * @param dishText           사용자가 말한 요리 이름 그대로 (예: "냉면"). 분류·특징이면 빈 문자열
 * @param preferenceText     사용자가 원한 특징·요리 표현 그대로 (예: "차가운 요리")
 * @param targetIndex        레시피 자세히일 때 직전 추천 카드 번호(1부터). 모르면 0
 * @param targetName         레시피 자세히일 때 말한 레시피 이름. 없으면 빈 문자열
 * @param removed            이어짐일 때 지우라고 한 조건 종류 (RefineCondition 이름). 없으면 빈 목록
 * @param detailQuestion     레시피 자세히일 때 질문 종류 (DetailQuestion 이름). 다른 말이면 빈 값
 * @param fridgeScope        냉장고 보기 범위 (FridgeScope 이름). 다른 말이면 ALL
 */
public record IntentExtraction(
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
        String detailQuestion,
        String fridgeScope
) {

    private static final Set<String> QUESTION_NAMES = names(DetailQuestion.values());
    private static final Set<String> CONDITION_NAMES = names(RefineCondition.values());
    private static final Set<String> SCOPE_NAMES = names(FridgeScope.values());

    // 아무것도 꺼내지 않은 결과 (스텁, OpenAI 꺼내기가 실패했지만 꺼낼 값이 필요 없는 의도)
    public static IntentExtraction empty() {
        return new IntentExtraction(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 값을 정리한다. 말하지 않은 칸(null)은 null로 둔다.
     * 조리 시간이 0 이하면 null, 재료 이름·키워드는 앞뒤 공백을 지우고 빈 이름을 뺀다. 목록은 중복을 빼고, 키워드는 6개까지.
     * 요리 이름·원한 표현·대상 레시피 이름이 비어 있으면 null, 대상 카드 번호가 0 이하면 null.
     * 뺄 조건·질문 종류·냉장고 범위는 정해진 이름만 남긴다 (소문자는 대문자로).
     */
    public IntentExtraction sanitize() {
        return new IntentExtraction(
                maxMinutes == null || maxMinutes <= 0 ? null : maxMinutes,
                cleanNames(includeIngredients, name -> name, null),
                cleanNames(excludeIngredients, name -> name, null),
                useSelectedOnly,
                keywords == null ? null : cleanNames(keywords, name -> name, null).stream().limit(IntentResult.MAX_KEYWORDS).toList(),
                blankToNull(dishText),
                blankToNull(preferenceText),
                targetIndex == null || targetIndex <= 0 ? null : targetIndex,
                blankToNull(targetName),
                cleanNames(removed, name -> name.toUpperCase(Locale.ROOT), CONDITION_NAMES),
                named(detailQuestion, QUESTION_NAMES),
                named(fridgeScope, SCOPE_NAMES)
        );
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String named(String value, Set<String> allowed) {
        if (value == null) {
            return null;
        }
        String name = value.strip().toUpperCase(Locale.ROOT);
        return allowed.contains(name) ? name : null;
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> cleanNames(List<String> names, UnaryOperator<String> transform, Set<String> allowed) {
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

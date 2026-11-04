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
 * @param intent             RECOMMEND / GENERAL
 * @param wantedTastes       원하는 맛 (Member.Taste 이름)
 * @param moodCategories     원하는 레시피 분류 (recipes.category 값)
 * @param maxMinutes         최대 조리 시간(분). 말하지 않았으면 0
 * @param includeIngredients 꼭 넣고 싶은 재료 이름 (말한 그대로)
 * @param excludeIngredients 빼고 싶은 재료 이름 (말한 그대로)
 * @param useSelectedOnly    고른·말한 재료를 꼭 쓰는 레시피만 원하는지
 */
public record IntentResult(
        ChatIntent intent,
        List<String> wantedTastes,
        List<String> moodCategories,
        Integer maxMinutes,
        List<String> includeIngredients,
        List<String> excludeIngredients,
        Boolean useSelectedOnly
) {

    private static final Set<String> TASTE_NAMES = Arrays.stream(Member.Taste.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    // 의도만 있고 조건은 하나도 말하지 않은 결과
    public static IntentResult general() {
        return new IntentResult(ChatIntent.GENERAL, null, null, null, null, null, null);
    }

    /**
     * 값을 정리한다. 말하지 않은 칸(null)은 null로 둔다.
     * 의도가 없으면 GENERAL, 맛은 Member.Taste에 있는 값만, 분류는 allowedCategories에 있는 값만 남긴다.
     * 조리 시간이 0 이하면 null, 재료 이름은 앞뒤 공백을 지우고 빈 이름을 뺀다. 목록은 중복을 뺀다.
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
                useSelectedOnly
        );
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

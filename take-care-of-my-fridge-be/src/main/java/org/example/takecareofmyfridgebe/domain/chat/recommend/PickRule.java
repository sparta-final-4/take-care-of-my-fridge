package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 요청 조건이 있는 추천에서 카드를 고르는 기준 (RecommendationPicker). 스프링에 의존하지 않는 순수 값이다.
 * <ul>
 *   <li>말한 조건을 모두 맞춘 후보(matchesAll)를 먼저 고른다</li>
 *   <li>3개가 안 되면 분류가 맞고, 원하는 맛과 반대 맛만 있지 않고, 온도 말과 반대가 아닌 후보(canFill)로만 채운다. 원하는 맛이 있으면 그 맛인 레시피,
 *       매운맛이면 매울 수 있는 레시피 순으로 먼저 채운다(fillPriority)</li>
 *   <li>그것도 없으면 있는 만큼(2장·1장)만 고른다</li>
 * </ul>
 * 말한 조건이 없으면(requestCount 0) 지금처럼 정렬 순서대로 3장이다.
 *
 * @param requestCount   말한 조건 수: 요리 이름 키워드(말했으면 1) + 원하는 맛 수 + 분류(말했으면 1).
 *                       후보의 요청 일치 수(Ranking.requestMatches)와 같은 기준으로 센다
 * @param categories     말한 분류. 비어 있으면 분류는 보지 않는다
 * @param wantedTastes   원하는 맛
 * @param oppositeTastes 원하는 맛과 반대인 맛 (ChatTasteOppositeProperties, 예: 매운맛 ↔ 순한맛)
 * @param recipeTastes   후보 레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
 * @param maybeSpicyIds  매울 수 있는 레시피 ID (매운 정도 MILD·HOT)
 * @param temperatureMismatched 온도 말과 반대인 레시피 ID (TemperatureWordChecker.mismatched). 빈자리를 채우는 데 쓰지 않는다
 */
public record PickRule(
        int requestCount,
        Set<String> categories,
        Set<Member.Taste> wantedTastes,
        Set<Member.Taste> oppositeTastes,
        Map<Long, Set<Member.Taste>> recipeTastes,
        Set<Long> maybeSpicyIds,
        Set<Long> temperatureMismatched
) {

    public static final PickRule NONE = new PickRule(0, Set.of(), Set.of(), Set.of(), Map.of(), Set.of());

    // 채울 때 순서: 원하는 맛인 레시피 → (매운맛을 원했으면) 매울 수 있는 레시피 → 나머지
    private static final int WANTED_TASTE = 0;
    private static final int MAYBE_SPICY = 1;
    private static final int OTHER = 2;

    public PickRule {
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        wantedTastes = wantedTastes == null ? Set.of() : Set.copyOf(wantedTastes);
        oppositeTastes = oppositeTastes == null ? Set.of() : Set.copyOf(oppositeTastes);
        recipeTastes = recipeTastes == null ? Map.of() : Map.copyOf(recipeTastes);
        maybeSpicyIds = maybeSpicyIds == null ? Set.of() : Set.copyOf(maybeSpicyIds);
        temperatureMismatched = temperatureMismatched == null ? Set.of() : Set.copyOf(temperatureMismatched);
    }

    public PickRule(int requestCount, Set<String> categories, Set<Member.Taste> wantedTastes, Set<Member.Taste> oppositeTastes,
                    Map<Long, Set<Member.Taste>> recipeTastes, Set<Long> maybeSpicyIds) {
        this(requestCount, categories, wantedTastes, oppositeTastes, recipeTastes, maybeSpicyIds, Set.of());
    }

    // 온도 말과 반대인 레시피를 채운 기준
    public PickRule withTemperatureMismatched(Set<Long> ids) {
        return new PickRule(requestCount, categories, wantedTastes, oppositeTastes, recipeTastes, maybeSpicyIds, ids);
    }

    /**
     * @param opposites 맛 하나와 반대인 맛 (ChatTasteOppositeProperties::oppositesOf)
     */
    public static PickRule of(RecipeQuery query, RecipeCandidates found,
                              Function<Member.Taste, Collection<Member.Taste>> opposites) {
        Set<Member.Taste> wanted = query.wantedTastes().isEmpty()
                ? Set.of()
                : EnumSet.copyOf(query.wantedTastes());
        boolean keyword = !query.keywords().isEmpty() || query.keywordText() != null;
        int count = (keyword ? 1 : 0) + wanted.size() + (query.moodCategories().isEmpty() ? 0 : 1);
        Set<Member.Taste> opposite = EnumSet.noneOf(Member.Taste.class);
        wanted.forEach(taste -> opposite.addAll(opposites.apply(taste)));
        opposite.removeAll(wanted);
        return new PickRule(count, Set.copyOf(query.moodCategories()), wanted, opposite,
                found.recipeTastes(), found.maybeSpicyIds());
    }

    public boolean hasRequest() {
        return requestCount > 0;
    }

    public boolean categorySpoken() {
        return !categories.isEmpty();
    }

    // 말한 조건을 모두 맞춘 후보
    public boolean matchesAll(RecipeCandidate candidate) {
        return candidate.ranking().requestMatches() >= requestCount;
    }

    // 모두 맞춘 후보가 모자랄 때 채울 수 있는 후보: 분류가 맞고(말했을 때), 원하는 맛과 반대 맛만 있지 않고, 온도 말과 반대가 아니다
    public boolean canFill(RecipeCandidate candidate) {
        return categoryMatches(candidate) && !oppositeOnly(candidate)
                && !temperatureMismatched.contains(candidate.recipeId());
    }

    // 채울 때 앞에 둘수록 작은 값
    public int fillPriority(RecipeCandidate candidate) {
        Set<Member.Taste> tastes = recipeTastes.getOrDefault(candidate.recipeId(), Set.of());
        if (tastes.stream().anyMatch(wantedTastes::contains)) {
            return WANTED_TASTE;
        }
        if (wantedTastes.contains(Member.Taste.SPICY) && maybeSpicyIds.contains(candidate.recipeId())) {
            return MAYBE_SPICY;
        }
        return OTHER;
    }

    private boolean categoryMatches(RecipeCandidate candidate) {
        return categories.isEmpty() || (candidate.category() != null && categories.contains(candidate.category()));
    }

    // 반대 맛이 있고 원하는 맛은 없는 레시피 (매운맛을 원했는데 순한맛만 있는 레시피)
    private boolean oppositeOnly(RecipeCandidate candidate) {
        Set<Member.Taste> tastes = recipeTastes.getOrDefault(candidate.recipeId(), Set.of());
        return tastes.stream().anyMatch(oppositeTastes::contains)
                && tastes.stream().noneMatch(wantedTastes::contains)
                && !maybeSpicyWanted(candidate);
    }

    private boolean maybeSpicyWanted(RecipeCandidate candidate) {
        return wantedTastes.contains(Member.Taste.SPICY) && maybeSpicyIds.contains(candidate.recipeId());
    }
}

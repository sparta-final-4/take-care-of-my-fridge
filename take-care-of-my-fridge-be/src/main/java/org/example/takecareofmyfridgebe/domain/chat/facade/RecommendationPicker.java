package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 정렬된 레시피 후보에서 추천할 3개를 고른다. 스프링에 의존하지 않는 순수 클래스.
 * <ol>
 *   <li>키워드로 맞은(이름·늘린 이름·재료) 레시피가 두 키워드 이상에 걸쳐 있으면 키워드마다 가장 앞선 레시피를 하나씩 먼저 고른다.
 *       순서는 요리 이름이 맞은 것, 그다음 AI가 뽑은 키워드 순서다 (볶음면 10건, 탕수육 5건 → 탕수육 1 + 볶음면 1 + 나머지 1).
 *       남는 자리는 정렬 순서대로 채운다</li>
 *   <li>3개가 모두 같은 분류면 4~10위에서 처음 나오는 다른 분류 레시피로 바꾼다. 키워드별로 고른 자리는 바꾸지 않고,
 *       정렬 순서로 채운 자리 중 마지막 것만 바꾼다 (키워드별로 이미 고르게 골랐으므로)</li>
 * </ol>
 * 바꿔 넣은 카드는 alternative(다른 제안)로 표시해 AI 설명·카드 이유가 요청과 다른 제안이라고 말하게 한다.
 * 후보가 3개보다 적으면 있는 만큼 고른다.
 */
public final class RecommendationPicker {

    public static final int PICK_COUNT = 3;

    // 요리 이름(dishText)이 맞은 레시피의 키워드 순서. AI 키워드보다 앞에 둔다
    private static final int DISH_NAME_ORDER = -1;

    private RecommendationPicker() {
    }

    public static List<RecipeCandidate> pick(List<RecipeCandidate> ranked) {
        return pick(ranked, List.of());
    }

    /**
     * @param keywords AI가 뽑은 키워드 (순서대로 고른다)
     */
    public static List<RecipeCandidate> pick(List<RecipeCandidate> ranked, List<String> keywords) {
        List<RecipeCandidate> picked = new ArrayList<>(spreadByKeyword(ranked, keywords));
        int fixed = picked.size();
        for (RecipeCandidate candidate : ranked) {
            if (picked.size() >= PICK_COUNT) {
                break;
            }
            if (!picked.contains(candidate)) {
                picked.add(candidate);
            }
        }
        if (picked.size() < PICK_COUNT || fixed >= PICK_COUNT || !allSameCategory(picked)) {
            return List.copyOf(picked);
        }
        String category = picked.get(0).category();
        ranked.stream()
                .filter(candidate -> !picked.contains(candidate))
                .filter(candidate -> candidate.category() != null && !candidate.category().equals(category))
                .findFirst()
                .ifPresent(other -> picked.set(PICK_COUNT - 1, other.asAlternative()));
        return List.copyOf(picked);
    }

    // 키워드마다 가장 앞선 레시피 하나씩 (요리 이름 → AI 키워드 순서, 최대 3개). 두 키워드 이상에 걸쳐 있을 때만
    private static List<RecipeCandidate> spreadByKeyword(List<RecipeCandidate> ranked, List<String> keywords) {
        Map<Integer, RecipeCandidate> firstByKeyword = new LinkedHashMap<>();
        for (RecipeCandidate candidate : ranked) {
            if (isKeywordMatch(candidate)) {
                firstByKeyword.putIfAbsent(keywordOrder(candidate.reason().keywordMatch(), keywords), candidate);
            }
        }
        if (firstByKeyword.size() < 2) {
            return List.of();
        }
        return firstByKeyword.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .limit(PICK_COUNT)
                .toList();
    }

    // 요리 이름이 맞았으면 맨 앞, 늘린 이름·재료로 맞았으면 그 키워드의 순서 (목록에 없으면 맨 뒤)
    private static int keywordOrder(KeywordMatch match, List<String> keywords) {
        if (match.type() == KeywordMatch.Type.NAME) {
            return DISH_NAME_ORDER;
        }
        int index = match.keyword() == null ? -1 : keywords.indexOf(match.keyword());
        return index < 0 ? keywords.size() : index;
    }

    private static boolean isKeywordMatch(RecipeCandidate candidate) {
        KeywordMatch match = candidate.reason().keywordMatch();
        return match != null && match.strong();
    }

    // 분류가 없는 레시피가 끼어 있으면 같은 분류로 보지 않는다
    private static boolean allSameCategory(List<RecipeCandidate> picked) {
        String category = picked.get(0).category();
        return category != null && picked.stream().allMatch(candidate -> Objects.equals(candidate.category(), category));
    }
}

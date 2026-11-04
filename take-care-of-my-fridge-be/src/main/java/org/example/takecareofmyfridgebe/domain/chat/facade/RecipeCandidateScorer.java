package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 없이 규칙으로 레시피 후보를 고르고 정렬한다. 스프링에 의존하지 않는 순수 클래스.
 * <p>
 * 정렬은 점수를 합산하지 않고, 앞 단계가 같을 때만 다음 단계로 비교한다.
 * 1. 사용자 요청 일치 수(요리 이름 키워드·원하는 맛·분류) → 2. 선택 재료 겹친 수 → (선택 재료가 있을 때만) 재료 많이 모자람이 아닌 것 먼저
 * → 3. 임박 점수 합 → 4. 선호 맛 겹친 수 → 5. 보유 비율 → 6. 없는 재료 수 적은 순 → 7. 레시피 id.
 * 선택 재료가 같으면 바로 만들 수 있는 레시피를 먼저 둔다. 임박 재료 하나 때문에 부족한 재료가 많은 레시피가 위로 오지 않게 하려는 것이다.
 * 임박을 선호 맛보다 앞에 둔다. 회원 선호 맛은 대부분 레시피와 1~2개씩 겹쳐서, 뒤에 두지 않으면 임박 재료가 순위에 거의 반영되지 않는다.
 * <p>
 * 보유 비율이 기준(minOwnedRatio) 미만인 레시피는 기준 이상 레시피를 위 순서로 먼저 고른 뒤, 10개가 안 차면 같은 순서로 채운다.
 * 흔한 임박 재료(양파·대파 등)만 겹치고 나머지를 거의 사야 하는 레시피가 위로 올라오지 않게 하려는 것이다.
 * 단, 선택 재료가 기본 양념을 빼고 2개 이상이고 레시피가 그 재료를 전부 쓰면 기준 미만이어도 먼저 고른다 (모자람 표시는 그대로 남는다).
 * 메시지에서 말한 재료는 선택 재료로 합쳐서 오므로 1단계에서 세지 않고 2단계에서 센다.
 * 요리 이름 키워드("냉면")로 레시피 검색에서 찾은 레시피(RecipeQuery.keywordMatches)는 냉장고 재료와 겹치지 않아도 후보에 넣고,
 * 재료를 많이 사야 해도 1차 후보로 둔다 (사용자가 그 요리를 말했으므로).
 * useSelectedOnly면 선택 재료(기본 양념 제외)를 하나도 쓰지 않는 레시피는 뺀다.
 * 선택 재료는 같은 종류 묶음(RecipeQuery.selectedIngredientGroups, 예: 버섯 → 표고버섯·양송이버섯)으로 센다.
 * 묶음 안의 재료를 하나라도 쓰면 그 선택 재료를 쓴 것으로 보고, 후보도 묶음 안의 재료로 모은다.
 * 냉장고 수량은 재료마다 단위(g·개·대…)가 달라 비교하지 않는다. 기본 양념은 어느 단계에서도 세지 않는다.
 */
public class RecipeCandidateScorer {

    public static final int LIMIT = 10;

    // 임박 점수: D-0 12, D-1 10, D-2 8, D-3 6 (임박 기준 D-3은 FridgeItemStatus와 같다)
    private static final int IMMINENT_BASE_SCORE = 12;
    private static final int IMMINENT_STEP = 2;

    private static final Comparator<RecipeCandidate> ORDER = order(false);
    // 선택 재료가 있을 때: 선택 재료 겹친 수 바로 뒤에 "재료 많이 모자람이 아닌 것 먼저"
    private static final Comparator<RecipeCandidate> ORDER_WITH_SELECTED = order(true);

    private static Comparator<RecipeCandidate> order(boolean selectedFirstReady) {
        Comparator<RecipeCandidate> order = Comparator
                .comparingInt((RecipeCandidate c) -> c.ranking().requestMatches()).reversed()
                .thenComparing(Comparator.comparingInt((RecipeCandidate c) -> c.ranking().selectedMatches()).reversed());
        if (selectedFirstReady) {
            // false(모자라지 않음)가 앞
            order = order.thenComparing(c -> c.reason().lacksManyIngredients());
        }
        return order
                .thenComparing(Comparator.comparingInt((RecipeCandidate c) -> c.ranking().imminentScore()).reversed())
                .thenComparing(Comparator.comparingInt((RecipeCandidate c) -> c.ranking().tasteMatches()).reversed())
                .thenComparing(RecipeCandidateScorer::compareOwnedRatioDesc)
                .thenComparingInt(c -> c.ranking().missingCount())
                .thenComparingLong(RecipeCandidate::recipeId);
    }

    // 선택 재료 전부를 쓰는 레시피를 보유 비율 기준에서 빼는 최소 선택 재료 수 (기본 양념 제외)
    private static final int MIN_SELECTED_FOR_EXCEPTION = 2;

    // 비율 비교의 부동소수 오차 허용치 (예: 2/5와 0.4)
    private static final double RATIO_EPSILON = 1e-9;

    private final Set<String> excludedCategories;
    private final double minOwnedRatio;

    /**
     * @param excludedCategories 후보에서 뺄 레시피 분류
     * @param minOwnedRatio      보유 비율 기준. 미만인 레시피는 뒤로 보낸다 (0이면 기준 없음)
     */
    public RecipeCandidateScorer(Collection<String> excludedCategories, double minOwnedRatio) {
        this.excludedCategories = excludedCategories == null ? Set.of() : Set.copyOf(excludedCategories);
        this.minOwnedRatio = minOwnedRatio;
    }

    /**
     * 냉장고 재료를 후보 검색에 쓸 모양으로 준비한다 (프론트 useRecipeFridgeMatch와 같은 규칙).
     * 직접 입력(CUSTOM)·밀키트(MEALKIT)·기본 양념·기한 지난 줄은 빼고, 같은 재료는 가장 빠른 소비기한 하나로 합친다.
     */
    public static List<FridgeStock> prepareFridge(List<FridgeItemResponse> items) {
        Map<Long, FridgeStock> byIngredient = new LinkedHashMap<>();
        for (FridgeItemResponse item : items) {
            if (!isUsable(item)) {
                continue;
            }
            FridgeStock stock = new FridgeStock(item.ingredientId(), item.ingredientName(), item.dDay());
            byIngredient.merge(item.ingredientId(), stock,
                    (existing, added) -> added.dDay() < existing.dDay() ? added : existing);
        }
        return List.copyOf(byIngredient.values());
    }

    private static boolean isUsable(FridgeItemResponse item) {
        return item.ingredientId() != null
                && item.ingredientType() != IngredientType.CUSTOM
                && item.ingredientType() != IngredientType.MEALKIT
                && !item.basicSeasoning()
                && item.status() != FridgeItemStatus.EXPIRED;
    }

    /**
     * 레시피 재료를 조회할 재료 ID: 냉장고·선택 재료와 그 같은 종류 묶음(후보를 모으는 재료), 제외 재료(필터용).
     */
    public Set<Long> lookupIngredientIds(RecipeQuery query, List<FridgeStock> fridge) {
        Set<Long> ids = new LinkedHashSet<>(matchIngredientIds(query, fridge));
        ids.addAll(query.excludeIngredientIds());
        return ids;
    }

    /**
     * 냉장고·선택 재료(같은 종류 묶음 포함) 중 기본 양념이 아닌 재료가 하나라도 들어간 레시피 ID.
     * 냉장고에 없는 선택 재료도 포함한다. 기본 양념만 겹치는 레시피는 후보가 아니다.
     */
    public Set<Long> candidateRecipeIds(RecipeQuery query, List<FridgeStock> fridge, List<RecipeCandidateIngredient> rows) {
        Set<Long> matchIds = matchIngredientIds(query, fridge);
        Set<Long> recipeIds = rows.stream()
                .filter(row -> !row.basicSeasoning() && matchIds.contains(row.ingredientId()))
                .map(RecipeCandidateIngredient::recipeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        recipeIds.addAll(query.keywordMatches().keySet());
        return recipeIds;
    }

    /**
     * 후보를 거르고 정렬해 상위 10개를 고른다. useSelectedOnly면 선택 재료를 하나도 쓰지 않는 레시피는 뺀다.
     * 보유 비율 기준 이상인 레시피와 선택 재료(기본 양념 제외 2개 이상)를 전부 쓰는 레시피를 먼저 고르고, 10개가 안 되면 나머지로 채운다.
     * 부족한 재료 목록은 비워 두고, withMissingIngredients로 채운다.
     *
     * @param rows         lookupIngredientIds로 조회한 레시피 재료 줄
     * @param infos        후보 레시피 정보 (없는 레시피는 건너뛴다)
     * @param recipeTastes 레시피별 맛 (비어 있으면 맛 단계는 모두 0)
     */
    public RecipeCandidates rank(
            RecipeQuery query,
            List<FridgeStock> fridge,
            Collection<Member.Taste> memberTastes,
            List<RecipeCandidateIngredient> rows,
            Map<Long, RecipeCandidateInfo> infos,
            Map<Long, Set<Member.Taste>> recipeTastes
    ) {
        Map<Long, FridgeStock> fridgeById = fridge.stream()
                .collect(Collectors.toMap(FridgeStock::ingredientId, stock -> stock, (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<RecipeCandidateIngredient>> rowsByRecipe = rows.stream()
                .collect(Collectors.groupingBy(RecipeCandidateIngredient::recipeId, LinkedHashMap::new, Collectors.toList()));
        Set<Member.Taste> preferred = toTasteSet(memberTastes);

        // 선택 재료만 쓰기는 기본 양념 뺀 선택 재료가 있을 때만 건다 (없으면 모든 레시피가 빠지므로)
        List<Long> nonBasicSelected = nonBasicSelectedIds(query, rows);
        int selectedCount = nonBasicSelected.size();
        boolean selectedOnly = query.useSelectedOnly() && selectedCount > 0;

        List<RecipeCandidate> scored = new ArrayList<>();
        for (Long recipeId : candidateRecipeIds(query, fridge, rows)) {
            RecipeCandidateInfo info = infos.get(recipeId);
            List<RecipeCandidateIngredient> recipeRows = rowsByRecipe.getOrDefault(recipeId, List.of());
            if (info == null || isFilteredOut(query, info, recipeRows)
                    || (selectedOnly && countSelectedMatches(query, nonBasicSelected, recipeRows) == 0)) {
                continue;
            }
            scored.add(score(query, nonBasicSelected, info, recipeRows, fridgeById, preferred,
                    recipeTastes.getOrDefault(recipeId, Set.of())));
        }

        boolean fallback = query.selectedIngredientIds().isEmpty()
                || scored.stream().allMatch(candidate -> candidate.ranking().selectedMatches() == 0);

        // 1차: 보유 비율 기준 이상이거나 선택 재료(2개 이상)를 전부 쓰는 레시피, 2차: 나머지. 각각 같은 정렬 순서
        Comparator<RecipeCandidate> order = selectedCount > 0 ? ORDER_WITH_SELECTED : ORDER;
        List<RecipeCandidate> top = Stream.concat(
                        scored.stream().filter(candidate -> isFirstTier(query, candidate, selectedCount)).sorted(order),
                        scored.stream().filter(candidate -> !isFirstTier(query, candidate, selectedCount)).sorted(order))
                .limit(LIMIT)
                .toList();
        return new RecipeCandidates(top, fallback);
    }

    // 사용자가 고른 재료(기본 양념 제외 2개 이상)를 전부 쓰는 레시피는 재료를 많이 사야 해도 1차 후보로 둔다 (구매 링크로 보완).
    // 선택이 1개면 그 재료를 쓰면서 기준 이상인 레시피가 충분히 많아 예외를 두지 않는다
    // 요리 이름 키워드로 찾은 레시피도 사용자가 말한 요리라 1차 후보로 둔다
    private static boolean isFirstTier(RecipeQuery query, RecipeCandidate candidate, int selectedCount) {
        if (query.keywordMatches().containsKey(candidate.recipeId())) {
            return true;
        }
        boolean usesAllSelected = selectedCount >= MIN_SELECTED_FOR_EXCEPTION
                && candidate.ranking().selectedMatches() == selectedCount;
        return usesAllSelected || !candidate.reason().lacksManyIngredients();
    }

    // 레시피가 쓰는 선택 재료 수. 선택 재료의 같은 종류 묶음 안 재료(기본 양념 제외)를 하나라도 쓰면 쓴 것으로 센다
    private static int countSelectedMatches(
            RecipeQuery query,
            List<Long> nonBasicSelected,
            List<RecipeCandidateIngredient> recipeRows
    ) {
        Set<Long> recipeIngredientIds = recipeRows.stream()
                .filter(row -> !row.basicSeasoning())
                .map(RecipeCandidateIngredient::ingredientId)
                .collect(Collectors.toSet());
        return (int) nonBasicSelected.stream()
                .filter(selectedId -> query.selectedGroupOf(selectedId).stream().anyMatch(recipeIngredientIds::contains))
                .count();
    }

    // 레시피가 쓰는 선택 재료(같은 종류 묶음 포함)의 이름. 레시피에 나온 순서이고 레시피에 실제로 쓰인 이름이다
    private static List<String> usedSelectedNames(
            RecipeQuery query,
            List<Long> nonBasicSelected,
            List<RecipeCandidateIngredient> nonBasic
    ) {
        Set<Long> groupIds = new HashSet<>();
        nonBasicSelected.forEach(selectedId -> groupIds.addAll(query.selectedGroupOf(selectedId)));
        return nonBasic.stream()
                .filter(row -> groupIds.contains(row.ingredientId()))
                .map(RecipeCandidateIngredient::ingredientName)
                .distinct()
                .toList();
    }

    // 레시피가 쓰는 선택 재료 중 냉장고에 없는 것의 이름. 같은 종류 묶음에서 레시피에 쓰인 재료가 하나라도 냉장고에 있으면 가진 것으로 본다.
    // 이름은 레시피에 실제로 쓰인 재료 이름이다 (예: 선택 "버섯" → 레시피의 "표고버섯")
    private static List<String> missingSelectedNames(
            RecipeQuery query,
            List<Long> nonBasicSelected,
            List<RecipeCandidateIngredient> nonBasic,
            Map<Long, FridgeStock> fridgeById
    ) {
        Set<String> names = new LinkedHashSet<>();
        for (Long selectedId : nonBasicSelected) {
            Set<Long> group = query.selectedGroupOf(selectedId);
            List<RecipeCandidateIngredient> used = nonBasic.stream()
                    .filter(row -> group.contains(row.ingredientId()))
                    .toList();
            if (used.isEmpty() || used.stream().anyMatch(row -> fridgeById.containsKey(row.ingredientId()))) {
                continue;
            }
            used.forEach(row -> names.add(row.ingredientName()));
        }
        return List.copyOf(names);
    }

    // 기본 양념을 뺀 선택 재료 (중복 제거). 레시피 재료 줄에서 기본 양념으로 확인된 재료만 뺀다
    // (어느 레시피에도 없는 재료는 기본 양념이 아닌 것으로 센다. 그런 재료를 전부 쓰는 레시피는 없으므로 예외도 생기지 않는다)
    private static List<Long> nonBasicSelectedIds(RecipeQuery query, List<RecipeCandidateIngredient> rows) {
        Set<Long> basicIds = rows.stream()
                .filter(RecipeCandidateIngredient::basicSeasoning)
                .map(RecipeCandidateIngredient::ingredientId)
                .collect(Collectors.toSet());
        return query.selectedIngredientIds().stream()
                .distinct()
                .filter(id -> !basicIds.contains(id))
                .toList();
    }

    /**
     * 후보마다 냉장고에 없는 재료(기본 양념 제외)를 근거에 채운다.
     *
     * @param fridge          prepareFridge로 준비한 냉장고 재료
     * @param nonBasicRows    후보 레시피의 기본 양념을 뺀 재료 전체 (레시피에 나온 순)
     */
    public static RecipeCandidates withMissingIngredients(
            RecipeCandidates result,
            List<FridgeStock> fridge,
            List<RecipeCandidateIngredient> nonBasicRows
    ) {
        Set<Long> ownedIds = fridge.stream()
                .map(FridgeStock::ingredientId)
                .collect(Collectors.toSet());
        Map<Long, List<MissingIngredient>> missingByRecipe = new LinkedHashMap<>();
        for (RecipeCandidateIngredient row : nonBasicRows) {
            if (row.basicSeasoning() || ownedIds.contains(row.ingredientId())) {
                continue;
            }
            List<MissingIngredient> missing = missingByRecipe.computeIfAbsent(row.recipeId(), id -> new ArrayList<>());
            MissingIngredient ingredient = new MissingIngredient(row.ingredientId(), row.ingredientName());
            if (!missing.contains(ingredient)) {
                missing.add(ingredient);
            }
        }

        List<RecipeCandidate> candidates = result.candidates().stream()
                .map(candidate -> candidate.withReason(candidate.reason().withMissingIngredients(
                        missingByRecipe.getOrDefault(candidate.recipeId(), List.of()))))
                .toList();
        return new RecipeCandidates(candidates, result.fallback());
    }

    private boolean isFilteredOut(RecipeQuery query, RecipeCandidateInfo info, List<RecipeCandidateIngredient> recipeRows) {
        if (info.category() != null && excludedCategories.contains(info.category())) {
            return true;
        }
        Set<Long> excludeIds = Set.copyOf(query.excludeIngredientIds());
        if (recipeRows.stream().anyMatch(row -> excludeIds.contains(row.ingredientId()))) {
            return true;
        }
        // 조리 시간이 비어 있으면(MFDS 출처 등) 거르지 않는다
        return query.maxMinutes() != null
                && info.cookTimeMin() != null
                && info.cookTimeMin() > query.maxMinutes();
    }

    private RecipeCandidate score(
            RecipeQuery query,
            List<Long> nonBasicSelected,
            RecipeCandidateInfo info,
            List<RecipeCandidateIngredient> recipeRows,
            Map<Long, FridgeStock> fridgeById,
            Set<Member.Taste> preferred,
            Set<Member.Taste> tastes
    ) {
        List<RecipeCandidateIngredient> nonBasic = recipeRows.stream()
                .filter(row -> !row.basicSeasoning())
                .toList();

        // 냉장고에 있는 재료 (임박한 순, 같으면 이름 순)
        List<FridgeStock> owned = nonBasic.stream()
                .map(row -> fridgeById.get(row.ingredientId()))
                .filter(stock -> stock != null)
                .distinct()
                .sorted(Comparator.comparingLong(FridgeStock::dDay).thenComparing(FridgeStock::name))
                .toList();

        int selectedMatches = countSelectedMatches(query, nonBasicSelected, recipeRows);
        List<String> missingSelected = missingSelectedNames(query, nonBasicSelected, nonBasic, fridgeById);
        List<String> usedSelected = usedSelectedNames(query, nonBasicSelected, nonBasic);

        // 1단계: 사용자 요청 (요리 이름 키워드 + 원하는 맛 + 분류 일치). 말한 재료는 선택 재료로 합쳐져 2단계에서 센다
        List<String> matchedRequests = new ArrayList<>();
        List<Member.Taste> matchedWantedTastes = query.wantedTastes().stream()
                .distinct()
                .filter(tastes::contains)
                .toList();
        List<String> matchedKeywords = query.keywordMatches().getOrDefault(info.recipeId(), List.of());
        if (!matchedKeywords.isEmpty()) {
            matchedRequests.add("키워드: " + String.join(", ", matchedKeywords));
        }
        matchedWantedTastes.forEach(taste -> matchedRequests.add("맛: " + taste));
        if (info.category() != null && query.moodCategories().contains(info.category())) {
            matchedRequests.add("분류: " + info.category());
        }

        List<Member.Taste> matchedTastes = preferred.stream()
                .filter(tastes::contains)
                .toList();

        List<ImminentIngredient> imminent = owned.stream()
                .filter(stock -> stock.dDay() <= FridgeItemStatus.IMMINENT_DAYS)
                .map(stock -> new ImminentIngredient(stock.name(), stock.dDay()))
                .toList();
        int imminentScore = imminent.stream()
                .mapToInt(stock -> imminentScoreOf(stock.dDay()))
                .sum();

        // 조회 쿼리와 레시피 정보가 어긋나도 비율이 1을 넘지 않게 분모를 보정한다
        int nonBasicCount = Math.max(info.nonBasicIngredientCount(), owned.size());

        Ranking ranking = new Ranking(
                matchedRequests.size(),
                selectedMatches,
                matchedTastes.size(),
                imminentScore,
                owned.size(),
                nonBasicCount,
                nonBasicCount - owned.size()
        );
        Reason reason = new Reason(
                owned.stream().map(FridgeStock::name).toList(),
                imminent,
                matchedTastes,
                matchedWantedTastes,
                List.copyOf(matchedRequests),
                info.cookTimeMin() == null,
                ranking.ownedRatio() + RATIO_EPSILON < minOwnedRatio,
                List.of(),
                missingSelected,
                usedSelected
        );
        return new RecipeCandidate(info.recipeId(), info.name(), info.category(), info.cookTimeMin(), ranking, reason);
    }

    // D-0 12, D-1 10, D-2 8, D-3 6. 임박 목록에는 D-3 이내만 들어온다
    private static int imminentScoreOf(long dDay) {
        return IMMINENT_BASE_SCORE - IMMINENT_STEP * (int) dDay;
    }

    // 보유 비율 높은 순. 나눗셈 오차가 없도록 분수를 교차 곱으로 비교한다
    private static int compareOwnedRatioDesc(RecipeCandidate a, RecipeCandidate b) {
        long left = (long) a.ranking().ownedCount() * b.ranking().nonBasicIngredientCount();
        long right = (long) b.ranking().ownedCount() * a.ranking().nonBasicIngredientCount();
        return Long.compare(right, left);
    }

    private static Set<Long> matchIngredientIds(RecipeQuery query, List<FridgeStock> fridge) {
        Set<Long> ids = new HashSet<>();
        fridge.forEach(stock -> ids.add(stock.ingredientId()));
        query.selectedIngredientIds().forEach(selectedId -> ids.addAll(query.selectedGroupOf(selectedId)));
        return ids;
    }

    private static Set<Member.Taste> toTasteSet(Collection<Member.Taste> tastes) {
        Set<Member.Taste> set = EnumSet.noneOf(Member.Taste.class);
        if (tastes != null) {
            tastes.stream().filter(taste -> taste != null).forEach(set::add);
        }
        return set;
    }

    /**
     * 후보 검색에 쓰는 냉장고 재료 (같은 재료는 가장 빠른 소비기한으로 합친 것).
     *
     * @param dDay 오늘 기준 소비기한까지 남은 일수
     */
    public record FridgeStock(Long ingredientId, String name, long dDay) {
    }
}

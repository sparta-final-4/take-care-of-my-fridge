package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
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

/**
 * AI 없이 규칙으로 레시피 후보를 고르고 정렬한다. 스프링에 의존하지 않는 순수 클래스.
 * <p>
 * 정렬은 점수를 합산하지 않고, 앞 단계가 같을 때만 다음 단계로 비교한다.
 * 1. 사용자 요청 일치 수 → 2. 선택 재료 겹친 수 → 3. 임박 점수 합 → 4. 선호 맛 겹친 수
 * → 5. 보유 비율 → 6. 없는 재료 수 적은 순 → 7. 레시피 id.
 * 임박을 선호 맛보다 앞에 둔다. 회원 선호 맛은 대부분 레시피와 1~2개씩 겹쳐서, 뒤에 두지 않으면 임박 재료가 순위에 거의 반영되지 않는다.
 * 냉장고 수량은 재료마다 단위(g·개·대…)가 달라 비교하지 않는다. 기본 양념은 어느 단계에서도 세지 않는다.
 */
public class RecipeCandidateScorer {

    public static final int LIMIT = 10;

    // 임박 점수: D-0 12, D-1 10, D-2 8, D-3 6 (임박 기준 D-3은 FridgeItemStatus와 같다)
    private static final int IMMINENT_BASE_SCORE = 12;
    private static final int IMMINENT_STEP = 2;

    private static final Comparator<RecipeCandidate> ORDER = Comparator
            .comparingInt((RecipeCandidate c) -> c.ranking().requestMatches()).reversed()
            .thenComparing(Comparator.comparingInt((RecipeCandidate c) -> c.ranking().selectedMatches()).reversed())
            .thenComparing(Comparator.comparingInt((RecipeCandidate c) -> c.ranking().imminentScore()).reversed())
            .thenComparing(Comparator.comparingInt((RecipeCandidate c) -> c.ranking().tasteMatches()).reversed())
            .thenComparing(RecipeCandidateScorer::compareOwnedRatioDesc)
            .thenComparingInt(c -> c.ranking().missingCount())
            .thenComparingLong(RecipeCandidate::recipeId);

    private final Set<String> excludedCategories;

    public RecipeCandidateScorer(Collection<String> excludedCategories) {
        this.excludedCategories = excludedCategories == null ? Set.of() : Set.copyOf(excludedCategories);
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
     * 레시피 재료를 조회할 재료 ID: 냉장고·선택·포함 재료(후보를 모으는 재료)와 제외 재료(필터용).
     */
    public Set<Long> lookupIngredientIds(RecipeQuery query, List<FridgeStock> fridge) {
        Set<Long> ids = new LinkedHashSet<>(matchIngredientIds(query, fridge));
        ids.addAll(query.excludeIngredientIds());
        return ids;
    }

    /**
     * 냉장고·선택·포함 재료 중 기본 양념이 아닌 재료가 하나라도 들어간 레시피 ID.
     * 기본 양념만 겹치는 레시피는 후보가 아니다.
     */
    public Set<Long> candidateRecipeIds(RecipeQuery query, List<FridgeStock> fridge, List<RecipeCandidateIngredient> rows) {
        Set<Long> matchIds = matchIngredientIds(query, fridge);
        return rows.stream()
                .filter(row -> !row.basicSeasoning() && matchIds.contains(row.ingredientId()))
                .map(RecipeCandidateIngredient::recipeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 후보를 거르고 정렬해 상위 10개를 고른다.
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

        List<RecipeCandidate> scored = new ArrayList<>();
        for (Long recipeId : candidateRecipeIds(query, fridge, rows)) {
            RecipeCandidateInfo info = infos.get(recipeId);
            List<RecipeCandidateIngredient> recipeRows = rowsByRecipe.getOrDefault(recipeId, List.of());
            if (info == null || isFilteredOut(query, info, recipeRows)) {
                continue;
            }
            scored.add(score(query, info, recipeRows, fridgeById, preferred,
                    recipeTastes.getOrDefault(recipeId, Set.of())));
        }

        boolean fallback = query.selectedIngredientIds().isEmpty()
                || scored.stream().allMatch(candidate -> candidate.ranking().selectedMatches() == 0);

        List<RecipeCandidate> top = scored.stream()
                .sorted(ORDER)
                .limit(LIMIT)
                .toList();
        return new RecipeCandidates(top, fallback);
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
            RecipeCandidateInfo info,
            List<RecipeCandidateIngredient> recipeRows,
            Map<Long, FridgeStock> fridgeById,
            Set<Member.Taste> preferred,
            Set<Member.Taste> tastes
    ) {
        Set<Long> selectedIds = Set.copyOf(query.selectedIngredientIds());
        Set<Long> includeIds = Set.copyOf(query.includeIngredientIds());

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

        int selectedMatches = (int) nonBasic.stream()
                .map(RecipeCandidateIngredient::ingredientId)
                .filter(selectedIds::contains)
                .distinct()
                .count();

        // 1단계: 사용자 요청 (포함 재료 + 원하는 맛 + 분류 일치)
        List<String> matchedRequests = new ArrayList<>();
        nonBasic.stream()
                .filter(row -> includeIds.contains(row.ingredientId()))
                .map(RecipeCandidateIngredient::ingredientName)
                .distinct()
                .forEach(name -> matchedRequests.add("재료: " + name));
        query.wantedTastes().stream()
                .distinct()
                .filter(tastes::contains)
                .forEach(taste -> matchedRequests.add("맛: " + taste));
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
                List.copyOf(matchedRequests),
                info.cookTimeMin() == null
        );
        return new RecipeCandidate(info.recipeId(), info.name(), ranking, reason);
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
        ids.addAll(query.selectedIngredientIds());
        ids.addAll(query.includeIngredientIds());
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

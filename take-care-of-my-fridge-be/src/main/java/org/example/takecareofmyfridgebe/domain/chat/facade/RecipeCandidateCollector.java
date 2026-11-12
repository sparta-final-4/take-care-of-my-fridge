package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateConditions;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeTasteLevel;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.CandidateHit;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.Condition;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담 추천 레시피 후보를 모은다. RecipeCandidateFacade.findCandidates의 첫 단계와 보충 단계를 맡는다.
 * 여러 도메인(냉장고·레시피·상품·검색)을 조회해서 facade 층에 둔다.
 * <p>
 * 후보는 레시피 후보 검색(ES, RecipeCandidateSearchService)으로 모은다. 분류·키워드를 말했으면 요청 조건만 넣은 검색을 한 번 더 해서 합친다
 * (냉장고 재료가 많이 겹치는 레시피에 밀려 "디저트"만 맞은 레시피가 빠지지 않게).
 * ES를 쓰지 않는 설정이거나 검색이 실패하면 DB 경로(냉장고·선택 재료로 레시피 역조회 + 키워드마다 레시피 검색)로 모은다.
 * 보충: 키워드로 맞은 레시피가 없을 때 레시피 검색(벡터는 한 번만), 피할 맛 때문에 모자랄 때 피할 맛 없이 다시 검색.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeCandidateCollector {

    // 키워드 하나당 레시피 검색 결과 수 (관련도 순)
    private static final int KEYWORD_SEARCH_SIZE = 10;

    private final RecipeService recipeService;
    private final RecipeSearchService recipeSearchService;
    private final RecipeCandidateSearchService recipeCandidateSearchService;
    private final RecipeSearchProperties recipeSearchProperties;
    private final ChatCandidateProperties chatCandidateProperties;

    /**
     * 피할 맛 없이 다시 검색해(요청만 검색도 같이) 처음 검색에 없던 레시피만 모은다. 이 레시피들은 피할 맛 레시피로 본다.
     * 다시 검색이 실패하면 채우지 않는다 (처음 결과는 그대로 쓴다).
     */
    CandidatePool searchAvoided(RecipeQuery query, List<FridgeStock> fridge, Set<Long> alreadyFound) {
        try {
            CandidatePool withoutAvoid = collectFromEs(query, fridge, true, alreadyFound);
            log.debug("상담 후보 피할 맛 다시 검색 - memberId: {}, 맛: {}, 새로 찾은 수: {}",
                    query.memberId(), query.avoidTastes(), withoutAvoid.recipeIds().size());
            return withoutAvoid;
        } catch (IOException | RuntimeException e) {
            log.warn("상담 후보 피할 맛 다시 검색 실패, 채우지 않음 - memberId: {}, 원인: {}", query.memberId(), e.toString());
            return CandidatePool.EMPTY;
        }
    }

    /**
     * 모은 후보.
     *
     * @param recipeIds            후보 레시피 ID
     * @param rows                 후보의 재료 줄 (정렬용)
     * @param keywordHits          키워드로 찾은 레시피 ID별 벡터 검색으로만 찾았는지 (카드 이유·정렬 1단계용으로 가른다)
     * @param tasteLevels          ES 후보 검색 결과의 레시피 ID별 맛·매운 정도 (DB 경로·벡터 보충은 비어 있다)
     * @param rowsNonBasicComplete rows가 후보마다 기본 양념 뺀 재료 전체인지 (그러면 부족한 재료를 다시 조회하지 않는다)
     * @param route                어디서 모았는지 (ES / ES+요청만 / ES+벡터 / ES+요청만+벡터 / DB 대체). 로그용
     * @param counts               검색별로 더한 후보 수. 로그용
     * @param esCalls              ES 후보 검색 호출 수 (전체 1 + 요청만 0~1). 로그용
     * @param vectorCalled         레시피 검색에서 벡터 검색을 불렀는지. 로그용
     */
    record CandidatePool(
            Set<Long> recipeIds,
            List<RecipeCandidateIngredient> rows,
            Map<Long, Boolean> keywordHits,
            Map<Long, RecipeTasteLevel> tasteLevels,
            boolean rowsNonBasicComplete,
            String route,
            String counts,
            int esCalls,
            boolean vectorCalled
    ) {
        static final CandidatePool EMPTY = new CandidatePool(Set.of(), List.of(), Map.of(), Map.of(), true, "", "", 0, false);

        // 다른 후보를 뒤에 합친다 (같은 레시피는 앞의 것을 둔다)
        CandidatePool plus(CandidatePool other) {
            Set<Long> ids = new LinkedHashSet<>(recipeIds);
            ids.addAll(other.recipeIds());
            Map<Long, Boolean> hits = new LinkedHashMap<>(keywordHits);
            other.keywordHits().forEach(hits::putIfAbsent);
            Map<Long, RecipeTasteLevel> levels = new HashMap<>(tasteLevels);
            other.tasteLevels().forEach(levels::putIfAbsent);
            List<RecipeCandidateIngredient> mergedRows = Stream.concat(rows.stream(), other.rows().stream()).distinct().toList();
            return new CandidatePool(ids, mergedRows, hits, levels, rowsNonBasicComplete && other.rowsNonBasicComplete(), route,
                    counts, esCalls, vectorCalled || other.vectorCalled());
        }

        CandidatePool withLog(String newRoute, String newCounts) {
            return new CandidatePool(recipeIds, rows, keywordHits, tasteLevels, rowsNonBasicComplete, newRoute, newCounts, esCalls,
                    vectorCalled);
        }
    }

    // ES로 모으고, ES를 쓰지 않거나 실패하면 DB 경로로 모은다
    CandidatePool collectCandidates(RecipeQuery query, List<FridgeStock> fridge, RecipeCandidateScorer scorer) {
        if (!recipeSearchProperties.useElasticSearch()) {
            log.warn("상담 후보 ES 검색을 쓰지 않는 설정이라 DB로 모음 - memberId: {}", query.memberId());
            return collectFromDb(query, fridge, scorer);
        }
        try {
            return collectFromEs(query, fridge, false, Set.of());
        } catch (IOException | RuntimeException e) {
            log.warn("상담 후보 ES 검색 실패, DB로 모음 - memberId: {}, 원인: {}", query.memberId(), e.toString());
            return collectFromDb(query, fridge, scorer);
        }
    }

    /**
     * ES 후보 검색으로 모은다. 분류·키워드가 있으면 요청 조건만 넣은 검색을 한 번 더 해서 합친다 (각 최대 100건).
     * 키워드로 맞은 레시피가 없으면 RecipeCandidateFacade.findCandidates가 레시피 검색(벡터 보충, searchKeywordsOnce)으로 더한다. 그 후보는 ES 거르기(시간·피할 맛 등)를 거치지 않아
     * 정렬(isFilteredOut)과 피할 맛 처리에서 다시 거른다.
     * 재료는 합친 후보 전체를 한 번에 읽는다 (기본 양념 뺀 재료).
     *
     * @param withoutAvoidTastes 피할 맛 없이 검색할지 (피할 맛 레시피로 다시 채울 때)
     * @param skipIds            이미 모은 레시피 (결과에서 빼고, 재료도 읽지 않는다)
     */
    private CandidatePool collectFromEs(RecipeQuery query, List<FridgeStock> fridge, boolean withoutAvoidTastes,
                                        Set<Long> skipIds) throws IOException {
        Condition base = RecipeCandidateConditions.from(query, fridge, chatCandidateProperties.excludedCategories());
        Condition condition = withoutAvoidTastes ? base.withoutAvoidTastes() : base;
        Set<Long> recipeIds = new LinkedHashSet<>();
        Map<Long, Boolean> keywordHits = new LinkedHashMap<>();
        Map<Long, RecipeTasteLevel> tasteLevels = new HashMap<>();
        List<String> route = new ArrayList<>(List.of("ES"));
        List<String> counts = new ArrayList<>();
        int esCalls = 1;

        int before = recipeIds.size();
        addHits(recipeCandidateSearchService.search(condition), skipIds, recipeIds, keywordHits, tasteLevels);
        counts.add("전체 " + (recipeIds.size() - before));
        if (RecipeCandidateConditions.hasRequest(condition)) {
            before = recipeIds.size();
            addHits(recipeCandidateSearchService.search(RecipeCandidateConditions.requestOnly(condition)), skipIds, recipeIds,
                    keywordHits, tasteLevels);
            esCalls++;
            route.add("요청만");
            counts.add("요청만 새로 " + (recipeIds.size() - before));
        }
        return new CandidatePool(recipeIds, readNonBasicRows(recipeIds), keywordHits, tasteLevels, true,
                String.join("+", route), String.join(", ", counts), esCalls, false);
    }

    /**
     * ES 경로의 키워드 보충. 레시피 검색으로 키워드를 AI가 뽑은 순서대로 찾되, 벡터 검색은 한 번만 쓴다
     * (글자로 0건인 키워드 중 앞의 1개만 벡터로 찾고, 그다음 키워드는 찾지 않는다. 글자로 걸리는 레시피는 ES 후보에 이미 있다).
     * 이미 모은 레시피는 빼고, 재료는 새로 찾은 레시피만 읽는다.
     */
    CandidatePool searchKeywordsOnce(RecipeQuery query, Set<Long> skipIds) {
        Set<Long> recipeIds = new LinkedHashSet<>();
        Map<Long, Boolean> keywordHits = new LinkedHashMap<>();
        KeywordSearch search = searchKeywords(query);
        search.hits().forEach((recipeId, vectorOnly) -> {
            if (!skipIds.contains(recipeId)) {
                recipeIds.add(recipeId);
                keywordHits.put(recipeId, vectorOnly);
            }
        });
        // 레시피 검색으로 찾은 후보는 ES 후보 검색의 거르기를 거치지 않는다. 정렬 단계가 다시 거르는 것:
        // 제외 분류·최대 시간(isFilteredOut), 선택 재료만 쓰기(rank), 피할 맛·매운 정도(rankAvoiding), 기본 양념이 아닌 뺄 재료(isFilteredOut).
        // 정렬 단계의 재료 줄에는 기본 양념이 없어서, 뺄 재료가 기본 양념이면 여기서 뺀다
        removeWithExcludedIngredients(query, recipeIds, keywordHits);
        return new CandidatePool(recipeIds, readNonBasicRows(recipeIds), keywordHits, Map.of(), true, "", "", 0,
                search.vectorCalled());
    }

    // 기본 양념 뺀 재료 줄. 후보가 없으면 조회하지 않는다 (RecipeService도 빈 입력이면 빈 목록이지만 호출부에서 먼저 막는다)
    List<RecipeCandidateIngredient> readNonBasicRows(Collection<Long> recipeIds) {
        return recipeIds.isEmpty() ? List.of() : recipeService.findNonBasicIngredients(recipeIds);
    }

    // 뺄 재료(기본 양념 포함)가 들어간 레시피를 후보에서 뺀다. 뺄 재료가 없거나 후보가 없으면 조회하지 않는다
    private void removeWithExcludedIngredients(RecipeQuery query, Set<Long> recipeIds, Map<Long, Boolean> keywordHits) {
        if (recipeIds.isEmpty() || query.excludeIngredientIds().isEmpty()) {
            return;
        }
        Set<Long> excluded = recipeService.findCandidateIngredients(query.excludeIngredientIds()).stream()
                .map(RecipeCandidateIngredient::recipeId)
                .filter(recipeIds::contains)
                .collect(Collectors.toSet());
        recipeIds.removeAll(excluded);
        keywordHits.keySet().removeAll(excluded);
    }

    // 검색 결과를 후보에 더한다. 이미 모은 레시피(skipIds)는 뺀다. 키워드로 맞은 레시피는 키워드 결과로 표시하고, 맛·매운 정도를 담는다
    private static void addHits(List<CandidateHit> hits, Set<Long> skipIds, Set<Long> recipeIds, Map<Long, Boolean> keywordHits,
                                Map<Long, RecipeTasteLevel> tasteLevels) {
        for (CandidateHit hit : hits) {
            if (skipIds.contains(hit.recipeId())) {
                continue;
            }
            recipeIds.add(hit.recipeId());
            tasteLevels.putIfAbsent(hit.recipeId(), hit.tasteLevel());
            if (hit.matched(RecipeCandidateSearchService.MATCH_KEYWORD)) {
                keywordHits.put(hit.recipeId(), false);
            }
        }
    }

    /**
     * DB 경로 (ES 대체). 냉장고·선택 재료(같은 종류 묶음 포함)가 들어간 레시피를 재료로 역조회하고,
     * 요리 이름 키워드는 레시피 검색으로 찾아 더한다 (냉장고 재료와 겹치지 않아도).
     */
    private CandidatePool collectFromDb(RecipeQuery query, List<FridgeStock> fridge, RecipeCandidateScorer scorer) {
        // 키워드로 찾은 레시피는 냉장고 재료와 겹치지 않을 수 있어 기본 양념 뺀 재료 전체를 더 받는다
        KeywordSearch search = searchKeywords(query);
        Map<Long, Boolean> keywordHits = search.hits();
        List<RecipeCandidateIngredient> keywordRows = readNonBasicRows(keywordHits.keySet());
        Set<Long> lookupIds = scorer.lookupIngredientIds(query, fridge);
        List<RecipeCandidateIngredient> rows = Stream.concat(
                        (lookupIds.isEmpty() ? List.<RecipeCandidateIngredient>of() : recipeService.findCandidateIngredients(lookupIds)).stream(),
                        keywordRows.stream())
                .distinct()
                .toList();
        Set<Long> recipeIds = new LinkedHashSet<>(scorer.candidateRecipeIds(query, fridge, rows));
        recipeIds.addAll(keywordHits.keySet());
        return new CandidatePool(recipeIds, rows, keywordHits, Map.of(), false, "DB 대체",
                "재료 역조회·키워드 " + recipeIds.size(), 0, search.vectorCalled());
    }

    // 키워드마다 레시피 검색(관련도 순)으로 레시피를 찾는다. 레시피 ID별로 벡터 검색으로만 찾았는지(true)를 돌려준다.
    // 한 키워드라도 키워드 검색으로 찾았으면 false. 검색이 실패한 키워드는 건너뛴다
    /**
     * 키워드마다 레시피 검색으로 찾은 결과.
     *
     * @param hits         레시피 ID별 벡터 검색으로만 찾았는지
     * @param vectorCalled 벡터 검색을 불렀는지 (로그용)
     */
    private record KeywordSearch(Map<Long, Boolean> hits, boolean vectorCalled) {
    }

    // 키워드 중복은 의도 결과 정리(IntentResult.sanitize)에서 빼지만, 여기서도 순서를 지키며 한 번 더 뺀다
    private KeywordSearch searchKeywords(RecipeQuery query) {
        Map<Long, Boolean> hits = new LinkedHashMap<>();
        List<String> keywords = query.keywords().stream().distinct().toList();
        if (keywords.isEmpty()) {
            return new KeywordSearch(hits, false);
        }
        List<String> summary = new ArrayList<>();
        boolean vectorCalled = false;
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i);
            try {
                RecipeListRequest request = new RecipeListRequest(
                        RecipeSort.RELEVANCE, keyword, null, null, null, null, 1, KEYWORD_SEARCH_SIZE);
                RecipeSearchResult result = recipeSearchService.search(request);
                result.recipeIds().forEach(recipeId ->
                        hits.merge(recipeId, result.vectorFallback(), (before, now) -> before && now));
                summary.add(keyword + " " + result.recipeIds().size() + "건" + (result.vectorFallback() ? "(벡터)" : ""));
                if (!result.vectorFallback()) {
                    continue;
                }
                // 벡터 검색(글자로 0건)은 한 번의 추천에서 한 번만 쓴다. 그다음 키워드는 찾지 않는다
                vectorCalled = true;
                int skipped = keywords.size() - i - 1;
                if (skipped > 0) {
                    summary.add("나머지 " + skipped + "개 생략");
                }
                break;
            } catch (IOException | RuntimeException e) {
                summary.add(keyword + " 실패");
                log.warn("상담 추천 키워드 검색 실패, 건너뜀 - memberId: {}, keyword: {}, 원인: {}",
                        query.memberId(), keyword, e.toString());
            }
        }
        log.info("상담 추천 키워드 - memberId: {}, 요리 표현: {}, 검색: {}", query.memberId(), query.keywordText(), summary);
        return new KeywordSearch(hits, vectorCalled);
    }
}

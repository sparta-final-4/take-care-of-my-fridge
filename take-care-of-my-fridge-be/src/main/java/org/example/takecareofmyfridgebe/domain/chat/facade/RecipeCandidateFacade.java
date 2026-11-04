package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 상담 추천에 쓸 레시피 후보를 고른다 (AI 없이 규칙으로).
 * 냉장고·회원·레시피 서비스에서 필요한 값을 모으고, 거르기·정렬은 RecipeCandidateScorer에 맡긴다.
 * 선택 재료는 재료 서비스로 같은 종류 재료를 찾아 묶는다 (예: 버섯 → 표고버섯·양송이버섯, 후보 검색에서만).
 * 요리 이름 키워드("냉면")는 레시피 검색(ES)으로 레시피를 찾아 후보에 더한다 (냉장고 재료와 겹치지 않아도).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeCandidateFacade {

    // 키워드 하나당 레시피 검색 결과 수 (관련도 순)
    private static final int KEYWORD_SEARCH_SIZE = 10;

    private final FridgeService fridgeService;
    private final MemberService memberService;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;
    private final RecipeSearchService recipeSearchService;
    private final ChatCandidateProperties chatCandidateProperties;

    /**
     * 회원 냉장고와 선택 재료·선호 맛·임박 재료로 레시피 후보를 최대 10개 고른다.
     *
     * @param request 회원 ID, 선택 재료, 의도 판단에서 뽑은 요청 조건 (선택 재료 묶음은 여기서 채운다)
     * @return 정렬된 후보와 fallback 여부 (선택 재료가 없거나 하나도 겹치지 않으면 true)
     */
    public RecipeCandidates findCandidates(RecipeQuery request) {
        RecipeQuery grouped = withSelectedIngredientGroups(request);
        RecipeCandidateScorer scorer = new RecipeCandidateScorer(
                chatCandidateProperties.excludedCategories(), chatCandidateProperties.minOwnedRatio());

        List<FridgeStock> fridge = RecipeCandidateScorer.prepareFridge(
                fridgeService.getItems(grouped.memberId(), FridgeSortType.EXPIRATION));
        // 원하는 맛을 말했으면 회원 선호 맛은 쓰지 않는다 (원하는 맛은 1단계 요청에서만 센다)
        // 회원 도메인에 맛만 조회하는 메서드가 없어 내 정보 조회를 쓴다
        // 피할 맛은 회원 선호 맛보다 앞선다 (선호 맛에 매운맛이 있어도 "매운 거 말고"면 매운맛을 세지 않는다)
        List<Member.Taste> memberTastes = grouped.wantedTastes().isEmpty()
                ? memberService.getMyInfo(grouped.memberId()).tastes().stream()
                .filter(taste -> !grouped.avoidTastes().contains(taste))
                .toList()
                : List.of();

        // 키워드로 찾은 레시피는 냉장고 재료와 겹치지 않을 수 있어 기본 양념 뺀 재료 전체를 더 받고, 어떻게 맞았는지 가른다
        Map<Long, Boolean> keywordHits = searchKeywords(grouped);
        List<RecipeCandidateIngredient> keywordRows = recipeService.findNonBasicIngredients(keywordHits.keySet());
        RecipeQuery query = grouped.withKeywordMatches(classifyKeywordHits(grouped, keywordHits, keywordRows));

        List<RecipeCandidateIngredient> rows = Stream.concat(
                        recipeService.findCandidateIngredients(scorer.lookupIngredientIds(query, fridge)).stream(),
                        keywordRows.stream())
                .distinct()
                .toList();
        Set<Long> recipeIds = scorer.candidateRecipeIds(query, fridge, rows);
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);
        Map<Long, Set<Member.Taste>> recipeTastes = withSpicyIngredients(
                recipeService.findTastesByRecipeIds(recipeIds), recipeIds, query, memberTastes);

        RecipeCandidates ranked = rankAvoiding(scorer, query, fridge, memberTastes, rows, infos, recipeTastes);

        // 부족한 재료는 고른 10개만 조회한다
        List<Long> topRecipeIds = ranked.candidates().stream().map(RecipeCandidate::recipeId).toList();
        RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(
                ranked, fridge, recipeService.findNonBasicIngredients(topRecipeIds));
        return new RecipeCandidates(result.candidates(), result.fallback(), query.keywordsNotFound());
    }

    /**
     * 정해진 레시피들을 지금 냉장고·회원 선호 맛 기준으로 계산한다 (거르기·정렬 없이). 저장된 추천 카드를 다시 보여 줄 때 쓴다.
     * 레시피 정보·재료·맛·냉장고·회원 조회는 레시피 수와 상관없이 한 번씩이다. 없는(지워진) 레시피는 결과에서 빠진다.
     *
     * @return 레시피 ID별 후보 (임박·쓰는 재료·장볼 재료·모자람이 채워짐)
     */
    public Map<Long, RecipeCandidate> describeRecipes(Long memberId, Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        RecipeCandidateScorer scorer = new RecipeCandidateScorer(
                chatCandidateProperties.excludedCategories(), chatCandidateProperties.minOwnedRatio());
        List<FridgeStock> fridge = RecipeCandidateScorer.prepareFridge(
                fridgeService.getItems(memberId, FridgeSortType.EXPIRATION));
        List<Member.Taste> memberTastes = memberService.getMyInfo(memberId).tastes();
        List<RecipeCandidateIngredient> rows = recipeService.findNonBasicIngredients(recipeIds);
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);
        Map<Long, Set<Member.Taste>> recipeTastes = recipeService.findTastesByRecipeIds(recipeIds);

        List<RecipeCandidate> scored = scorer.scoreAll(memberId, fridge, memberTastes, rows, infos, recipeTastes, recipeIds);
        RecipeCandidates withMissing = RecipeCandidateScorer.withMissingIngredients(
                new RecipeCandidates(scored, false), fridge, rows);
        Map<Long, RecipeCandidate> byId = new LinkedHashMap<>();
        withMissing.candidates().forEach(candidate -> byId.put(candidate.recipeId(), candidate));
        return byId;
    }

    /**
     * 피할 맛인 레시피는 빼고 순위를 매긴다. 빼서 3개가 안 되면 그때만 피할 맛 레시피를 순위대로 뒤에 채운다.
     */
    private static RecipeCandidates rankAvoiding(RecipeCandidateScorer scorer, RecipeQuery query, List<FridgeStock> fridge,
                                                 List<Member.Taste> memberTastes, List<RecipeCandidateIngredient> rows,
                                                 Map<Long, RecipeCandidateInfo> infos, Map<Long, Set<Member.Taste>> recipeTastes) {
        if (query.avoidTastes().isEmpty()) {
            return scorer.rank(query, fridge, memberTastes, rows, infos, recipeTastes);
        }
        Set<Long> avoided = infos.keySet().stream()
                .filter(id -> recipeTastes.getOrDefault(id, Set.of()).stream().anyMatch(query.avoidTastes()::contains))
                .collect(Collectors.toSet());
        Map<Long, RecipeCandidateInfo> kept = new HashMap<>(infos);
        kept.keySet().removeAll(avoided);
        RecipeCandidates ranked = scorer.rank(query, fridge, memberTastes, rows, kept, recipeTastes);
        log.debug("상담 후보 피할 맛 - 맛: {}, 뺀 레시피 수: {}, 남은 후보 수: {}", query.avoidTastes(), avoided.size(),
                ranked.candidates().size());
        if (ranked.candidates().size() >= RecommendationPicker.PICK_COUNT || avoided.isEmpty()) {
            return ranked;
        }
        Map<Long, RecipeCandidateInfo> onlyAvoided = new HashMap<>(infos);
        onlyAvoided.keySet().retainAll(avoided);
        List<RecipeCandidate> refill = scorer.rank(query, fridge, memberTastes, rows, onlyAvoided, recipeTastes).candidates();
        return new RecipeCandidates(Stream.concat(ranked.candidates().stream(), refill.stream())
                .limit(RecipeCandidateScorer.LIMIT)
                .toList(), ranked.fallback());
    }

    /**
     * 맛 데이터에 매운맛이 없어도 매운 재료(설정값)가 들어 있으면 매운맛을 더한다. 원하는 맛·피할 맛·회원 선호 맛 어디에도
     * 매운맛이 없으면 조회하지 않는다 (재료 이름 조회 1번).
     */
    private Map<Long, Set<Member.Taste>> withSpicyIngredients(Map<Long, Set<Member.Taste>> recipeTastes, Set<Long> recipeIds,
                                                              RecipeQuery query, List<Member.Taste> memberTastes) {
        boolean spicyMatters = query.wantedTastes().contains(Member.Taste.SPICY)
                || query.avoidTastes().contains(Member.Taste.SPICY)
                || memberTastes.contains(Member.Taste.SPICY);
        if (!spicyMatters || recipeIds.isEmpty()) {
            return recipeTastes;
        }
        Set<Long> spicyIds = SpicyRecipeDetector.spicyRecipeIds(
                recipeService.findIngredientTexts(recipeIds), chatCandidateProperties.spicyIngredients());
        Map<Long, Set<Member.Taste>> merged = new HashMap<>(recipeTastes);
        for (Long id : spicyIds) {
            Set<Member.Taste> tastes = new HashSet<>(merged.getOrDefault(id, Set.of()));
            tastes.add(Member.Taste.SPICY);
            merged.put(id, tastes);
        }
        return merged;
    }

    // 키워드마다 레시피 검색(관련도 순)으로 레시피를 찾는다. 레시피 ID별로 벡터 검색으로만 찾았는지(true)를 돌려준다.
    // 한 키워드라도 키워드 검색으로 찾았으면 false. 검색이 실패한 키워드는 건너뛴다
    private Map<Long, Boolean> searchKeywords(RecipeQuery query) {
        Map<Long, Boolean> hits = new LinkedHashMap<>();
        if (query.keywords().isEmpty()) {
            return hits;
        }
        List<String> summary = new ArrayList<>();
        for (String keyword : query.keywords()) {
            try {
                RecipeListRequest request = new RecipeListRequest(
                        RecipeSort.RELEVANCE, keyword, null, null, null, null, 1, KEYWORD_SEARCH_SIZE);
                RecipeSearchResult result = recipeSearchService.search(request);
                result.recipeIds().forEach(recipeId ->
                        hits.merge(recipeId, result.vectorFallback(), (before, now) -> before && now));
                summary.add(keyword + " " + result.recipeIds().size() + "건" + (result.vectorFallback() ? "(벡터)" : ""));
            } catch (IOException | RuntimeException e) {
                summary.add(keyword + " 실패");
                log.warn("상담 추천 키워드 검색 실패, 건너뜀 - memberId: {}, keyword: {}, 원인: {}",
                        query.memberId(), keyword, e.toString());
            }
        }
        log.info("상담 추천 키워드 - memberId: {}, 요리 표현: {}, 검색: {}", query.memberId(), query.keywordText(), summary);
        return hits;
    }

    // 키워드로 찾은 레시피가 이름·늘린 이름·재료·쪼개짐·벡터 중 어떻게 맞았는지 가른다
    private Map<Long, KeywordMatch> classifyKeywordHits(
            RecipeQuery query,
            Map<Long, Boolean> keywordHits,
            List<RecipeCandidateIngredient> keywordRows
    ) {
        if (keywordHits.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(keywordHits.keySet());
        Map<Long, List<String>> ingredientNames = keywordRows.stream()
                .collect(Collectors.groupingBy(RecipeCandidateIngredient::recipeId,
                        Collectors.mapping(RecipeCandidateIngredient::ingredientName, Collectors.toList())));
        Map<Long, KeywordMatch> matches = new LinkedHashMap<>();
        keywordHits.forEach((recipeId, vectorOnly) -> {
            RecipeCandidateInfo info = infos.get(recipeId);
            if (info != null) {
                matches.put(recipeId, KeywordMatchClassifier.classify(query.keywordText(), query.keywords(), vectorOnly,
                        info.name(), ingredientNames.getOrDefault(recipeId, List.of())));
            }
        });
        return matches;
    }

    // 선택 재료마다 이름이 그 이름으로 끝나는 같은 종류 재료를 묶는다. 이름을 모르는 ID는 자신만 쓴다
    private RecipeQuery withSelectedIngredientGroups(RecipeQuery query) {
        if (query.selectedIngredientIds().isEmpty()) {
            return query;
        }
        Map<Long, Ingredient> selected = ingredientService.getIngredientsByIds(query.selectedIngredientIds());
        Map<String, List<Long>> sameKindByName = ingredientService.findSameKindIdsByNames(
                selected.values().stream().map(Ingredient::getName).toList());

        Map<Long, Set<Long>> groups = new HashMap<>();
        selected.forEach((id, ingredient) -> groups.put(id,
                new HashSet<>(sameKindByName.getOrDefault(ingredient.getName().trim(), List.of()))));
        return query.withSelectedIngredientGroups(groups);
    }
}

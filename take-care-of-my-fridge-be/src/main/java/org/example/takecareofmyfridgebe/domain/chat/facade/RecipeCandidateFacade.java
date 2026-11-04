package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
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
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    // 로그에 남길 키워드 검색 레시피 이름 수
    private static final int KEYWORD_LOG_LIMIT = 5;

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
        RecipeQuery query = withKeywordMatches(withSelectedIngredientGroups(request));
        RecipeCandidateScorer scorer = new RecipeCandidateScorer(
                chatCandidateProperties.excludedCategories(), chatCandidateProperties.minOwnedRatio());

        List<FridgeStock> fridge = RecipeCandidateScorer.prepareFridge(
                fridgeService.getItems(query.memberId(), FridgeSortType.EXPIRATION));
        // 원하는 맛을 말했으면 회원 선호 맛은 쓰지 않는다 (원하는 맛은 1단계 요청에서만 센다)
        // 회원 도메인에 맛만 조회하는 메서드가 없어 내 정보 조회를 쓴다
        List<Member.Taste> memberTastes = query.wantedTastes().isEmpty()
                ? memberService.getMyInfo(query.memberId()).tastes()
                : List.of();

        // 키워드로 찾은 레시피는 냉장고 재료와 겹치지 않을 수 있어 기본 양념 뺀 재료 전체를 더 받는다
        List<RecipeCandidateIngredient> rows = Stream.concat(
                        recipeService.findCandidateIngredients(scorer.lookupIngredientIds(query, fridge)).stream(),
                        recipeService.findNonBasicIngredients(query.keywordMatches().keySet()).stream())
                .distinct()
                .toList();
        Set<Long> recipeIds = scorer.candidateRecipeIds(query, fridge, rows);
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);
        logKeywordMatches(query, infos);
        Map<Long, Set<Member.Taste>> recipeTastes = recipeService.findTastesByRecipeIds(recipeIds);

        RecipeCandidates ranked = scorer.rank(query, fridge, memberTastes, rows, infos, recipeTastes);

        // 부족한 재료는 고른 10개만 조회한다
        List<Long> topRecipeIds = ranked.candidates().stream().map(RecipeCandidate::recipeId).toList();
        RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(
                ranked, fridge, recipeService.findNonBasicIngredients(topRecipeIds));
        boolean keywordsNotFound = !query.keywords().isEmpty() && query.keywordMatches().isEmpty();
        return new RecipeCandidates(result.candidates(), result.fallback(), keywordsNotFound);
    }

    // 키워드마다 레시피 검색(관련도 순)으로 레시피를 찾고, 레시피별로 맞은 키워드를 모은다. 검색이 실패한 키워드는 건너뛴다
    private RecipeQuery withKeywordMatches(RecipeQuery query) {
        if (query.keywords().isEmpty()) {
            return query;
        }
        Map<Long, List<String>> matches = new LinkedHashMap<>();
        for (String keyword : query.keywords()) {
            try {
                RecipeListRequest request = new RecipeListRequest(
                        RecipeSort.RELEVANCE, keyword, null, null, null, null, 1, KEYWORD_SEARCH_SIZE);
                recipeSearchService.search(request).recipeIds()
                        .forEach(recipeId -> matches.computeIfAbsent(recipeId, id -> new ArrayList<>()).add(keyword));
            } catch (IOException | RuntimeException e) {
                log.warn("상담 추천 키워드 검색 실패, 건너뜀 - memberId: {}, keyword: {}, 원인: {}",
                        query.memberId(), keyword, e.toString());
            }
        }
        return query.withKeywordMatches(matches);
    }

    private static void logKeywordMatches(RecipeQuery query, Map<Long, RecipeCandidateInfo> infos) {
        if (query.keywords().isEmpty()) {
            return;
        }
        List<String> names = query.keywordMatches().keySet().stream()
                .map(infos::get)
                .filter(info -> info != null)
                .map(info -> info.name() + "(" + info.category() + ")")
                .limit(KEYWORD_LOG_LIMIT)
                .toList();
        log.info("상담 추천 키워드 - memberId: {}, 키워드: {}, 요리 표현: {}, 찾은 레시피 {}개, 앞 {}개: {}",
                query.memberId(), query.keywords(), query.keywordText(), query.keywordMatches().size(),
                KEYWORD_LOG_LIMIT, names);
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

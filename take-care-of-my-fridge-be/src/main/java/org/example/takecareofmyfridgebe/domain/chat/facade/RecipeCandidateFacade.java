package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 상담 추천에 쓸 레시피 후보를 고른다 (AI 없이 규칙으로).
 * 냉장고·회원·레시피 서비스에서 필요한 값을 모으고, 거르기·정렬은 RecipeCandidateScorer에 맡긴다.
 * 선택 재료는 재료 서비스로 같은 종류 재료를 찾아 묶는다 (예: 버섯 → 표고버섯·양송이버섯).
 * <p>
 * 후보는 레시피 후보 검색(ES, RecipeCandidateSearchService)으로 모은다. 분류·키워드를 말했으면 요청 조건만 넣은 검색을 한 번 더 해서 합친다
 * (냉장고 재료가 많이 겹치는 레시피에 밀려 "디저트"만 맞은 레시피가 빠지지 않게).
 * 모은 후보의 재료·정보는 한 번씩 읽어 지금처럼 정렬한다. 맛·매운 정도는 ES 검색 결과(색인 때 정한 tastes·spicy_level)를 쓴다.
 * ES를 쓰지 않는 설정이거나 검색이 실패하면 DB 경로(냉장고·선택 재료로 레시피 역조회 + 키워드마다 레시피 검색)로 모은다.
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
    private final RecipeCandidateSearchService recipeCandidateSearchService;
    private final RecipeSearchProperties recipeSearchProperties;
    private final ChatCandidateProperties chatCandidateProperties;

    /**
     * 회원 냉장고와 선택 재료·선호 맛·임박 재료로 레시피 후보를 최대 10개 고른다.
     *
     * @param request 회원 ID, 선택 재료, 의도 판단에서 뽑은 요청 조건 (선택 재료 묶음은 여기서 채운다)
     * @return 정렬된 후보와 fallback 여부 (선택 재료가 없거나 하나도 겹치지 않으면 true)
     */
    public RecipeCandidates findCandidates(RecipeQuery request) {
        long started = System.nanoTime();
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

        CandidatePool pool = collectCandidates(grouped, fridge, scorer);
        PoolDetails details = loadDetails(pool, grouped, memberTastes);
        Ranked ranked = rankPool(scorer, grouped, fridge, memberTastes, pool, details, Set.of());

        // ES 경로에서 키워드로 맞은(이름·늘린 이름·재료) 레시피가 하나도 없으면 레시피 검색으로 보충한다. 벡터 검색은 한 번만
        // ("탕짜면"처럼 ES가 글자 일부("짜")로만 걸어 온 레시피는 맞은 것으로 치지 않는다)
        // 우회: ES가 키워드로 표시한 결과만 믿지 않고 쪼개짐(SPLIT)을 빼고 세는 것은, 레시피 인덱스 분석기가 키워드를 한 글자까지
        // 쪼개 상관없는 레시피를 키워드 결과로 걸기 때문이다(탕짜면 → "짜"). 키워드 _name이 겹쳐 표시가 빠지던 문제는 #599로 고쳐졌다.
        // 분석기가 고쳐지면 ES 키워드 표시만으로 정하도록 걷어낸다
        if (pool.rowsNonBasicComplete() && !grouped.keywords().isEmpty() && !hasStrongKeywordMatch(ranked.query())) {
            CandidatePool supplement = searchKeywordsOnce(grouped, pool.recipeIds());
            String route = pool.route() + "+벡터";
            String counts = pool.counts() + ", 벡터 보충 새로 " + supplement.recipeIds().size();
            // 새로 찾은 게 없어도 합친다 (벡터를 불렀는지 로그에 남기려고)
            pool = pool.plus(supplement);
            if (!supplement.recipeIds().isEmpty()) {
                details = details.plus(loadDetails(supplement, grouped, memberTastes));
                ranked = rankPool(scorer, grouped, fridge, memberTastes, pool, details, Set.of());
            }
            pool = pool.withLog(route, counts);
        }

        // ES 경로는 피할 맛을 검색에서 뺐으므로, 3개가 안 되면 피할 맛 없이 다시 검색해 새로 나온 레시피(피할 맛 레시피)를 뒤에 채운다
        int refilled = 0;
        int refillCalls = 0;
        if (pool.rowsNonBasicComplete() && !grouped.avoidTastes().isEmpty()
                && ranked.candidates().candidates().size() < RecommendationPicker.PICK_COUNT) {
            CandidatePool avoided = searchAvoided(grouped, fridge, pool.recipeIds());
            refillCalls = avoided.esCalls();
            if (!avoided.recipeIds().isEmpty()) {
                refilled = avoided.recipeIds().size();
                pool = pool.plus(avoided);
                details = details.plus(loadDetails(avoided, grouped, memberTastes));
                ranked = rankPool(scorer, grouped, fridge, memberTastes, pool, details, avoided.recipeIds());
            }
        }
        // ES 호출: 전체 검색(ES 경로면 1)·요청만 검색·피할 맛 다시 검색, 벡터는 레시피 검색에서 벡터를 불렀는지
        int fullCalls = Math.min(pool.esCalls(), 1);
        log.info("상담 후보 모으기 - memberId: {}, 경로: {}, 후보 {}건 ({}){}, 고른 후보 {}건, ES 호출 전체 {}·요청만 {}·피할 맛 재검색 {}, "
                        + "벡터 {}, {}ms",
                grouped.memberId(), pool.route(), pool.recipeIds().size(), pool.counts(),
                refilled > 0 ? ", 피할 맛 다시 채움 " + refilled + "건" : "", ranked.candidates().candidates().size(),
                fullCalls, pool.esCalls() - fullCalls, refillCalls, pool.vectorCalled() ? "호출" : "안 함",
                (System.nanoTime() - started) / 1_000_000);

        // 부족한 재료: ES 경로는 후보의 기본 양념 뺀 재료를 이미 다 읽었고, DB 경로는 고른 10개만 조회한다
        List<RecipeCandidateIngredient> nonBasicRows = pool.rowsNonBasicComplete()
                ? pool.rows()
                : readNonBasicRows(
                ranked.candidates().candidates().stream().map(RecipeCandidate::recipeId).toList());
        RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(ranked.candidates(), fridge, nonBasicRows);
        return new RecipeCandidates(result.candidates(), result.fallback(), ranked.query().keywordsNotFound(),
                ranked.candidates().keptAvoidTastes());
    }

    /**
     * 후보 레시피의 정보·맛·매운 정도. 맛에는 확실히 매운 레시피면 SPICY가 들어 있다.
     */
    private record PoolDetails(
            Map<Long, RecipeCandidateInfo> infos,
            Map<Long, Set<Member.Taste>> tastes,
            SpicyRecipes spicy
    ) {
        PoolDetails plus(PoolDetails other) {
            Map<Long, RecipeCandidateInfo> mergedInfos = new HashMap<>(infos);
            mergedInfos.putAll(other.infos());
            Map<Long, Set<Member.Taste>> mergedTastes = new HashMap<>(tastes);
            mergedTastes.putAll(other.tastes());
            return new PoolDetails(mergedInfos, mergedTastes, spicy.plus(other.spicy()));
        }
    }

    // 레시피 정보는 한 번 읽는다. 맛·매운 정도는 ES 검색 결과에 있는 레시피는 그 값을 쓰고,
    // ES 경로인데 검색 결과가 아닌 레시피(벡터 보충)는 ES에서 id로 한 번에 읽는다. 그래도 모르는 레시피와 DB 경로는 DB로 계산한다
    // 후보가 없으면 조회하지 않는다 (RecipeService도 빈 입력이면 빈 값을 돌려주지만 호출부에서 먼저 막는다)
    private PoolDetails loadDetails(CandidatePool pool, RecipeQuery query, List<Member.Taste> memberTastes) {
        Set<Long> recipeIds = pool.recipeIds();
        if (recipeIds.isEmpty()) {
            return new PoolDetails(Map.of(), Map.of(), SpicyRecipes.NONE);
        }
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);
        // rowsNonBasicComplete는 ES 경로(ES 검색·벡터 보충·피할 맛 다시 검색)에서만 true다
        TasteDetails tastes = tasteDetails(recipeIds, pool.tasteLevels(), pool.rowsNonBasicComplete(),
                spicyMatters(query, memberTastes), query.memberId());
        return new PoolDetails(infos, tastes.tastes(), tastes.spicy());
    }

    /**
     * 레시피 맛·매운 정도. 이미 아는 값(ES 검색 결과)을 쓰고, readEs면 모르는 레시피를 ES에서 id로 한 번에 읽는다.
     * 그래도 모르는 레시피(ES를 쓰지 않음·조회 실패·인덱스에 없음)만 DB로 계산한다.
     */
    private TasteDetails tasteDetails(Collection<Long> recipeIds, Map<Long, RecipeTasteLevel> known, boolean readEs,
                                      boolean spicyMatters, Long memberId) {
        Map<Long, RecipeTasteLevel> levels = new HashMap<>();
        List<Long> unknown = new ArrayList<>();
        for (Long id : recipeIds) {
            RecipeTasteLevel level = known.get(id);
            if (level == null) {
                unknown.add(id);
            } else {
                levels.put(id, level);
            }
        }
        if (!unknown.isEmpty() && readEs) {
            Map<Long, RecipeTasteLevel> read = readTasteLevels(unknown, memberId);
            levels.putAll(read);
            unknown = unknown.stream().filter(id -> !read.containsKey(id)).toList();
        }
        TasteDetails tastes = TasteDetails.of(levels);
        return unknown.isEmpty() ? tastes : tastes.plus(tasteDetailsFromDb(unknown, spicyMatters));
    }

    /**
     * 레시피별 맛과 매운 레시피 두 단계.
     *
     * @param tastes 레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
     */
    private record TasteDetails(Map<Long, Set<Member.Taste>> tastes, SpicyRecipes spicy) {

        // ES 값: HOT = 확실히 매움, MILD 이상 = 매울 수 있음. 색인할 때 HOT이면 맛에 SPICY를 더해 두었다
        static TasteDetails of(Map<Long, RecipeTasteLevel> levels) {
            Map<Long, Set<Member.Taste>> tastes = new HashMap<>();
            Set<Long> definite = new HashSet<>();
            Set<Long> maybe = new HashSet<>();
            levels.forEach((id, level) -> {
                if (!level.tastes().isEmpty()) {
                    tastes.put(id, level.tastes());
                }
                if (level.definitelySpicy()) {
                    definite.add(id);
                }
                if (level.maybeSpicy()) {
                    maybe.add(id);
                }
            });
            return new TasteDetails(tastes, new SpicyRecipes(Set.copyOf(definite), Set.copyOf(maybe)));
        }

        TasteDetails plus(TasteDetails other) {
            Map<Long, Set<Member.Taste>> merged = new HashMap<>(tastes);
            merged.putAll(other.tastes());
            return new TasteDetails(merged, spicy.plus(other.spicy()));
        }
    }

    // ES에서 레시피 id로 맛·매운 정도를 읽는다. 실패하면 빈 값을 돌려줘 호출하는 쪽이 DB로 계산하게 한다
    private Map<Long, RecipeTasteLevel> readTasteLevels(Collection<Long> recipeIds, Long memberId) {
        try {
            return recipeCandidateSearchService.findTasteLevels(recipeIds);
        } catch (IOException | RuntimeException e) {
            log.warn("레시피 맛·매운 정도 ES 조회 실패, DB로 계산 - memberId: {}, 레시피 수: {}, 원인: {}",
                    memberId, recipeIds.size(), e.toString());
            return Map.of();
        }
    }

    // DB 대체 경로와 ES에서 읽지 못한 레시피: 맛 데이터를 읽고, 매운맛이 중요하면 재료 이름으로 매운 정도를 계산해 확실히 매우면 SPICY를 더한다
    private TasteDetails tasteDetailsFromDb(Collection<Long> recipeIds, boolean spicyMatters) {
        SpicyRecipes spicy = spicyMatters ? spicyRecipes(recipeIds) : SpicyRecipes.NONE;
        return new TasteDetails(withDefinitelySpicy(recipeService.findTastesByRecipeIds(recipeIds), spicy), spicy);
    }

    // 정렬한 후보와, 키워드 결과(이름·재료 등으로 맞은 방식)를 채운 검색 조건
    private record Ranked(RecipeQuery query, RecipeCandidates candidates) {
    }

    private static Ranked rankPool(RecipeCandidateScorer scorer, RecipeQuery grouped, List<FridgeStock> fridge,
                                   List<Member.Taste> memberTastes, CandidatePool pool, PoolDetails details,
                                   Set<Long> knownAvoided) {
        RecipeQuery query = grouped.withKeywordMatches(
                classifyKeywordHits(grouped, pool.keywordHits(), pool.rows(), details.infos()));
        RecipeCandidates ranked = rankAvoiding(scorer, query, fridge, memberTastes, pool.rows(), details.infos(),
                details.tastes(), details.spicy(), knownAvoided, pool.recipeIds());
        return new Ranked(query, ranked);
    }

    /**
     * 피할 맛 없이 다시 검색해(요청만 검색도 같이) 처음 검색에 없던 레시피만 모은다. 이 레시피들은 피할 맛 레시피로 본다.
     * 다시 검색이 실패하면 채우지 않는다 (처음 결과는 그대로 쓴다).
     */
    private CandidatePool searchAvoided(RecipeQuery query, List<FridgeStock> fridge, Set<Long> alreadyFound) {
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
     * 정해진 레시피들을 지금 냉장고·회원 선호 맛 기준으로 계산한다 (거르기·정렬 없이). 저장된 추천 카드를 다시 보여 줄 때 쓴다.
     * 레시피 정보·재료·맛·냉장고·회원 조회는 레시피 수와 상관없이 한 번씩이다. 없는(지워진) 레시피는 결과에서 빠진다.
     *
     * @return 레시피 ID별 후보 (임박·쓰는 재료·장볼 재료·모자람이 채워짐)
     */
    public Map<Long, RecipeCandidate> describeRecipes(Long memberId, Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        List<FridgeStock> fridge = RecipeCandidateScorer.prepareFridge(
                fridgeService.getItems(memberId, FridgeSortType.EXPIRATION));
        return describe(memberId, fridge, recipeIds, recipeService.findTastesByRecipeIds(recipeIds));
    }

    /**
     * 밀키트 추천 재료: 밀키트에 연결된 레시피의 지금 냉장고 기준 계산(가진 재료 수)과 레시피 맛·매울 수 있는 레시피, 냉장고 밀키트 줄 이름.
     * 맛·매운 정도는 후보와 같은 기준으로 ES에서 id로 읽고, ES를 쓰지 않거나 읽지 못한 레시피는 DB 맛 데이터·재료 이름으로 계산한다.
     * 냉장고 조회는 한 번이고, 레시피가 없으면 레시피 조회를 하지 않는다.
     * 냉장고 밀키트 줄은 상품 ID 없이 구매 때의 상품 이름(raw_name)만 있어 이름으로 같은 밀키트를 가린다. 기한이 지난 줄은 뺀다.
     */
    public MealkitRecipes describeMealkitRecipes(Long memberId, Collection<Long> recipeIds) {
        List<FridgeItemResponse> items = fridgeService.getItems(memberId, FridgeSortType.EXPIRATION);
        List<String> fridgeMealkitNames = items.stream()
                .filter(item -> item.ingredientType() == IngredientType.MEALKIT && item.dDay() >= 0)
                .map(FridgeItemResponse::rawName)
                .toList();
        if (recipeIds.isEmpty()) {
            return new MealkitRecipes(Map.of(), Map.of(), Set.of(), fridgeMealkitNames);
        }
        // 피할 맛·원하는 맛을 여기서 모르므로 DB로 계산할 때도 매운 정도를 늘 계산한다 (밀키트 연결 레시피는 몇 건뿐이다)
        TasteDetails tastes = tasteDetails(recipeIds, Map.of(), recipeSearchProperties.useElasticSearch(), true, memberId);
        Map<Long, RecipeCandidate> recipes = describe(memberId, RecipeCandidateScorer.prepareFridge(items), recipeIds,
                tastes.tastes());
        return new MealkitRecipes(recipes, tastes.tastes(), tastes.spicy().maybe(), fridgeMealkitNames);
    }

    /**
     * @param recipes            레시피 ID별 지금 냉장고 기준 계산 (지워진 레시피는 없다)
     * @param recipeTastes        레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
     * @param maybeSpicyRecipeIds 매울 수 있는 레시피 ID (매운 정도 MILD·HOT)
     * @param fridgeMealkitNames  냉장고 밀키트 줄 이름 (기한 지난 줄 제외)
     */
    public record MealkitRecipes(
            Map<Long, RecipeCandidate> recipes,
            Map<Long, Set<Member.Taste>> recipeTastes,
            Set<Long> maybeSpicyRecipeIds,
            List<String> fridgeMealkitNames
    ) {
    }

    private Map<Long, RecipeCandidate> describe(Long memberId, List<FridgeStock> fridge, Collection<Long> recipeIds,
                                                Map<Long, Set<Member.Taste>> recipeTastes) {
        RecipeCandidateScorer scorer = new RecipeCandidateScorer(
                chatCandidateProperties.excludedCategories(), chatCandidateProperties.minOwnedRatio());
        List<Member.Taste> memberTastes = memberService.getMyInfo(memberId).tastes();
        List<RecipeCandidateIngredient> rows = recipeService.findNonBasicIngredients(recipeIds);
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(recipeIds);

        List<RecipeCandidate> scored = scorer.scoreAll(memberId, fridge, memberTastes, rows, infos, recipeTastes, recipeIds);
        RecipeCandidates withMissing = RecipeCandidateScorer.withMissingIngredients(
                new RecipeCandidates(scored, false), fridge, rows);
        Map<Long, RecipeCandidate> byId = new LinkedHashMap<>();
        withMissing.candidates().forEach(candidate -> byId.put(candidate.recipeId(), candidate));
        return byId;
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
    private record CandidatePool(
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
        private static final CandidatePool EMPTY = new CandidatePool(Set.of(), List.of(), Map.of(), Map.of(), true, "", "", 0, false);

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
    private CandidatePool collectCandidates(RecipeQuery query, List<FridgeStock> fridge, RecipeCandidateScorer scorer) {
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
     * 키워드로 맞은 레시피가 없으면 findCandidates가 레시피 검색(벡터 보충, searchKeywordsOnce)으로 더한다. 그 후보는 ES 거르기(시간·피할 맛 등)를 거치지 않아
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

    // 키워드로 이름·늘린 이름·재료가 맞은 레시피가 있는지 (쪼개짐·벡터는 맞은 것으로 치지 않는다)
    private static boolean hasStrongKeywordMatch(RecipeQuery query) {
        return query.keywordMatches().values().stream().anyMatch(KeywordMatch::strong);
    }

    /**
     * ES 경로의 키워드 보충. 레시피 검색으로 키워드를 AI가 뽑은 순서대로 찾되, 벡터 검색은 한 번만 쓴다
     * (글자로 0건인 키워드 중 앞의 1개만 벡터로 찾고, 그다음 키워드는 찾지 않는다. 글자로 걸리는 레시피는 ES 후보에 이미 있다).
     * 이미 모은 레시피는 빼고, 재료는 새로 찾은 레시피만 읽는다.
     */
    private CandidatePool searchKeywordsOnce(RecipeQuery query, Set<Long> skipIds) {
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
    private List<RecipeCandidateIngredient> readNonBasicRows(Collection<Long> recipeIds) {
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

    /**
     * 피할 맛인 레시피는 빼고 순위를 매긴다. 빼서 3개가 안 되면 그때만 피할 맛 레시피를 순위대로 뒤에 채운다.
     * 피할 맛이 매운맛이면 확실히 매운 레시피에 더해 매울 수 있는 레시피(홍고추·청고추)까지 뺀다.
     * 단 사용자가 콕 집은 요리(요리 이름이 레시피 이름에 통째로 맞음, KeywordMatch NAME)는 피할 맛이어도 빼지 않고 남기며,
     * 남긴 레시피와 그 맛을 결과에 담아 카드 이유에 알린다 ("매운 거 말고 김치찌개 줘" → 김치찌개 카드 + "매운 요리예요").
     *
     * @param knownAvoided 검색에서 피할 맛 레시피로 판단한 레시피 (피할 맛 없이 다시 검색해서 새로 나온 것)
     */
    private static RecipeCandidates rankAvoiding(RecipeCandidateScorer scorer, RecipeQuery query, List<FridgeStock> fridge,
                                                 List<Member.Taste> memberTastes, List<RecipeCandidateIngredient> rows,
                                                 Map<Long, RecipeCandidateInfo> infos, Map<Long, Set<Member.Taste>> recipeTastes,
                                                 SpicyRecipes spicy, Set<Long> knownAvoided, Set<Long> candidateIds) {
        if (query.avoidTastes().isEmpty()) {
            return scorer.rank(query, fridge, memberTastes, rows, infos, recipeTastes, candidateIds);
        }
        boolean avoidSpicy = query.avoidTastes().contains(Member.Taste.SPICY);
        Set<Long> avoided = infos.keySet().stream()
                .filter(id -> knownAvoided.contains(id)
                        || recipeTastes.getOrDefault(id, Set.of()).stream().anyMatch(query.avoidTastes()::contains)
                        || (avoidSpicy && spicy.maybe().contains(id)))
                .collect(Collectors.toSet());
        // 콕 집은 요리는 피할 맛보다 앞선다
        Map<Long, List<Member.Taste>> keptAvoid = new HashMap<>();
        for (Long id : avoided) {
            KeywordMatch match = query.keywordMatches().get(id);
            if (match != null && match.type() == KeywordMatch.Type.NAME) {
                keptAvoid.put(id, avoidedTastesOf(id, query, recipeTastes, spicy));
            }
        }
        avoided.removeAll(keptAvoid.keySet());
        if (!keptAvoid.isEmpty()) {
            log.info("상담 후보 피할 맛이지만 콕 집은 요리라 남김 - 피할 맛: {}, 레시피: {}", query.avoidTastes(), keptAvoid);
        }
        Map<Long, RecipeCandidateInfo> kept = new HashMap<>(infos);
        kept.keySet().removeAll(avoided);
        RecipeCandidates ranked = scorer.rank(query, fridge, memberTastes, rows, kept, recipeTastes, candidateIds)
                .withKeptAvoidTastes(keptAvoid);
        log.debug("상담 후보 피할 맛 - 맛: {}, 뺀 레시피 수: {}, 남은 후보 수: {}", query.avoidTastes(), avoided.size(),
                ranked.candidates().size());
        if (ranked.candidates().size() >= RecommendationPicker.PICK_COUNT || avoided.isEmpty()) {
            return ranked;
        }
        Map<Long, RecipeCandidateInfo> onlyAvoided = new HashMap<>(infos);
        onlyAvoided.keySet().retainAll(avoided);
        List<RecipeCandidate> refill = scorer.rank(query, fridge, memberTastes, rows, onlyAvoided, recipeTastes, candidateIds)
                .candidates();
        return new RecipeCandidates(Stream.concat(ranked.candidates().stream(), refill.stream())
                .limit(RecipeCandidateScorer.LIMIT)
                .toList(), ranked.fallback(), false, keptAvoid);
    }

    // 콕 집어 남긴 레시피가 어떤 피할 맛인지. 맛 데이터·매운 재료로 모르면(피할 맛 없이 다시 검색해서 찾음) 피할 맛 전체
    private static List<Member.Taste> avoidedTastesOf(Long id, RecipeQuery query, Map<Long, Set<Member.Taste>> recipeTastes,
                                                      SpicyRecipes spicy) {
        List<Member.Taste> tastes = query.avoidTastes().stream()
                .filter(taste -> recipeTastes.getOrDefault(id, Set.of()).contains(taste)
                        || (taste == Member.Taste.SPICY && spicy.maybe().contains(id)))
                .toList();
        return tastes.isEmpty() ? query.avoidTastes() : tastes;
    }

    /**
     * 재료로 가른 매운 레시피 두 단계.
     *
     * @param definite 확실히 매운 레시피 (청양고추·고춧가루·고추장·김치류 등). 원하는 맛 매운맛과 맞은 것으로 센다
     * @param maybe    매울 수 있는 레시피 (확실히 매운 것 + 홍고추·청고추, 정확한 이름 고추·붉은고추·고추냉이). 피할 맛 매운맛이면 이것까지 뺀다
     */
    private record SpicyRecipes(Set<Long> definite, Set<Long> maybe) {
        private static final SpicyRecipes NONE = new SpicyRecipes(Set.of(), Set.of());

        SpicyRecipes plus(SpicyRecipes other) {
            return new SpicyRecipes(
                    Stream.concat(definite.stream(), other.definite().stream()).collect(Collectors.toUnmodifiableSet()),
                    Stream.concat(maybe.stream(), other.maybe().stream()).collect(Collectors.toUnmodifiableSet()));
        }
    }

    // 원하는 맛·피할 맛·회원 선호 맛 어디에 매운맛이 있는지. 없으면 DB로 매운 정도를 계산하지 않는다
    private static boolean spicyMatters(RecipeQuery query, List<Member.Taste> memberTastes) {
        return query.wantedTastes().contains(Member.Taste.SPICY)
                || query.avoidTastes().contains(Member.Taste.SPICY)
                || memberTastes.contains(Member.Taste.SPICY);
    }

    // 재료 이름으로 매운 정도를 계산한다 (재료 이름 조회 1번). 레시피 색인(RecipeDocumentReader)과 같은 기준이다
    private SpicyRecipes spicyRecipes(Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return SpicyRecipes.NONE;
        }
        Map<Long, List<String>> texts = recipeService.findIngredientTexts(recipeIds);
        List<String> definiteWords = chatCandidateProperties.spicyIngredients();
        List<String> maybeWords = Stream.concat(definiteWords.stream(), chatCandidateProperties.mildSpicyIngredients().stream()).toList();
        return new SpicyRecipes(
                SpicyRecipeDetector.spicyRecipeIds(texts, definiteWords, chatCandidateProperties.spicyExclusions()),
                SpicyRecipeDetector.spicyRecipeIds(texts, maybeWords, chatCandidateProperties.mildSpicyExactIngredients(),
                        chatCandidateProperties.spicyExclusions()));
    }

    // 맛 데이터에 매운맛이 없어도 확실히 매운 재료가 들어 있으면 매운맛을 더한다 (매울 수 있는 것은 더하지 않는다)
    private static Map<Long, Set<Member.Taste>> withDefinitelySpicy(Map<Long, Set<Member.Taste>> recipeTastes, SpicyRecipes spicy) {
        if (spicy.definite().isEmpty()) {
            return recipeTastes;
        }
        Map<Long, Set<Member.Taste>> merged = new HashMap<>(recipeTastes);
        for (Long id : spicy.definite()) {
            Set<Member.Taste> tastes = new HashSet<>(merged.getOrDefault(id, Set.of()));
            tastes.add(Member.Taste.SPICY);
            merged.put(id, tastes);
        }
        return merged;
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
                // 벡터 검색(글자로 0건)은 한 번의 추천에서 한 번만 쓴다. 그다음 키워드는 찾지 않는다
                if (result.vectorFallback()) {
                    vectorCalled = true;
                    int skipped = keywords.size() - i - 1;
                    if (skipped > 0) {
                        summary.add("나머지 " + skipped + "개 생략");
                    }
                    break;
                }
            } catch (IOException | RuntimeException e) {
                summary.add(keyword + " 실패");
                log.warn("상담 추천 키워드 검색 실패, 건너뜀 - memberId: {}, keyword: {}, 원인: {}",
                        query.memberId(), keyword, e.toString());
            }
        }
        log.info("상담 추천 키워드 - memberId: {}, 요리 표현: {}, 검색: {}", query.memberId(), query.keywordText(), summary);
        return new KeywordSearch(hits, vectorCalled);
    }

    // 키워드로 찾은 레시피가 이름·늘린 이름·재료·쪼개짐·벡터 중 어떻게 맞았는지 가른다 (레시피 정보·재료는 후보 전체를 읽은 것을 쓴다)
    private static Map<Long, KeywordMatch> classifyKeywordHits(
            RecipeQuery query,
            Map<Long, Boolean> keywordHits,
            List<RecipeCandidateIngredient> rows,
            Map<Long, RecipeCandidateInfo> infos
    ) {
        if (query.keywords().isEmpty() && query.keywordText() == null) {
            return Map.of();
        }
        Map<Long, List<String>> ingredientNames = rows.stream()
                .filter(row -> !row.basicSeasoning() && keywordHits.containsKey(row.recipeId()))
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

    // 선택 재료마다 이름이 그 이름으로 끝나는 같은 종류 재료를 묶는다. 이름을 모르는 ID는 자신만 쓴다.
    // 기본 양념인 선택 재료는 빼서 어느 단계에서도 세지 않는다 (ES 경로 재료 줄에는 기본 양념이 없어 정렬이 가를 수 없다)
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
        Set<Long> basicSelected = selected.values().stream()
                .filter(Ingredient::isBasicSeasoning)
                .map(Ingredient::getId)
                .collect(Collectors.toSet());
        return query.withSelectedIngredientGroups(groups).withoutSelectedIngredients(basicSelected);
    }
}

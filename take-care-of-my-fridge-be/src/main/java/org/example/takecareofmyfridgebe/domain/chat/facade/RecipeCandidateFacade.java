package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateCollector.CandidatePool;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeTasteReader.SpicyRecipes;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeTasteReader.TasteDetails;
import org.example.takecareofmyfridgebe.domain.chat.recommend.KeywordMatchClassifier;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecommendationPicker;
import org.example.takecareofmyfridgebe.domain.chat.recommend.ScoringContext;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 상담 추천에 쓸 레시피 후보를 고른다 (AI 없이 규칙으로).
 * 냉장고·회원·레시피 서비스에서 필요한 값을 모으고, 거르기·정렬은 RecipeCandidateScorer에 맡긴다.
 * 선택 재료는 재료 서비스로 같은 종류 재료를 찾아 묶는다 (예: 버섯 → 표고버섯·양송이버섯).
 * <p>
 * 후보 모으기(ES·DB 경로, 보충)는 RecipeCandidateCollector, 맛·매운 정도 읽기는 RecipeTasteReader가 한다.
 * 모은 후보의 재료·정보는 한 번씩 읽어 지금처럼 정렬한다.
 * 정해진 레시피의 카드 설명 재료(지금 냉장고 기준 계산)는 RecipeCardDescriber가 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeCandidateFacade {

    private final FridgeService fridgeService;
    private final MemberService memberService;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;
    private final ChatCandidateProperties chatCandidateProperties;
    private final RecipeCandidateCollector recipeCandidateCollector;
    private final RecipeTasteReader recipeTasteReader;
    private final SimilarDishFinder similarDishFinder;

    /**
     * 회원 냉장고와 선택 재료·선호 맛·임박 재료로 레시피 후보를 최대 10개 고른다.
     *
     * @param request 회원 ID, 선택 재료, 의도 판단에서 뽑은 요청 조건 (선택 재료 묶음은 여기서 채운다)
     * @return 정렬된 후보와 fallback 여부 (선택 재료가 없거나 하나도 겹치지 않으면 true)
     */
    public RecipeCandidates findCandidates(RecipeQuery request) {
        long started = System.nanoTime();
        Context prepared = prepare(request);
        Collected first = collect(prepared);
        // 요리 이름 그대로 맞은 레시피가 없으면 비슷한 요리를 (캐시 또는 AI로) 한 번 구해 키워드에 더하고 글자로 찾는다
        Expanded expanded = addSimilarDishes(prepared, first);
        Context context = expanded.context();
        Collected collected = expanded.collected();
        // 키워드로 맞은 레시피가 없으면 레시피 검색 보충(벡터·이름 조각 포함), 맞은 게 있어도 요리 이름 그대로 맞은 게 없으면 이름 조각만
        collected = needsKeywordSupplement(context, collected)
                ? supplementKeywords(context, collected)
                : supplementDishFragments(context, collected);
        // 정렬은 여기서 한 번 한다. 피할 맛 때문에 3개가 안 되면 피할 맛 레시피를 더해 다시 정렬한다
        RecipeCandidates ranked = rankPool(context, collected);
        if (needsAvoidedRefill(context, collected, ranked)) {
            collected = refillAvoided(context, collected);
            ranked = collected.refilled() > 0 ? rankPool(context, collected) : ranked;
        }
        logCollected(context, collected, ranked, started);
        RecipeQuery query = context.query();
        // 요리 이름을 말했는데 제대로 맞은 후보가 없으면(벡터 후보만 남음, 또는 0장) 못 찾은 것으로 알린다
        boolean dishNotFound = dishAsked(query)
                && !anyStrong(ranked.candidates().stream().map(candidate -> candidate.reason().keywordMatch()));
        RecipeCandidates result = toResult(context, collected, ranked, dishNotFound);
        if (dishNotFound && result.candidates().isEmpty()) {
            return withEmptyReason(query, result);
        }
        if (dishNotFound) {
            log.info("상담 후보 요리 이름이 맞은 레시피가 없어 벡터 후보만 씀 - memberId: {}, 요리 이름: {}, 키워드: {}, 후보 {}건",
                    query.memberId(), query.keywordText(), query.keywords(), result.candidates().size());
        }
        return result;
    }

    // 키워드로 제대로 맞은(이름·늘린 이름·재료·이름 조각) 것이 있는지 (쪼개짐·벡터는 맞은 것으로 치지 않는다)
    private static boolean anyStrong(Stream<KeywordMatch> matches) {
        return matches.anyMatch(match -> match != null && match.strong());
    }

    // 요리 이름을 말했는지 (요리 이름, 또는 특징 없이 키워드만).
    // 말했으면 키워드로 찾은 후보(이름·비슷한 요리·재료·이름 조각·벡터)만 카드에 쓴다 (rankPool). 1~2장이어도 분류·냉장고 후보로 채우지 않는다.
    // 특징만 말한 추천("시원한 국물" → 냉국·냉면 키워드)은 요리 이름을 말한 것이 아니어서 지금처럼 분류·냉장고 후보로 카드를 채운다
    private static boolean dishAsked(RecipeQuery query) {
        return query.keywordText() != null || (!query.keywords().isEmpty() && query.preferenceText() == null);
    }

    // 요리 이름을 말한 추천의 카드 후보로 쓸 수 있는지: 키워드로 찾았고 글자 일부로만 걸린(쪼개짐) 것이 아님.
    // 벡터로 찾은 후보("김치찌게" → 김치찌개)는 남겨 "찾지 못해서 비슷한 걸 골랐어요"와 함께 보여 준다
    private static boolean keywordFound(KeywordMatch match) {
        return match != null && match.type() != KeywordMatch.Type.SPLIT;
    }

    // 카드가 0장이면 상담 답 문구를 고르도록 이유(못 찾음 / 뺄 재료 때문에 뺌)를 담는다. 그 밖이면 이유 없이 정해진 답이 나간다
    private RecipeCandidates withEmptyReason(RecipeQuery query, RecipeCandidates empty) {
        String dish = query.keywordText() != null ? query.keywordText() : query.keywords().get(0);
        RecipeCandidates.EmptyReason reason = recipeCandidateCollector.explainEmpty(query, dish);
        log.info("상담 후보 0개 이유 - memberId: {}, 요리 이름: {}, 이유: {}", query.memberId(), dish,
                reason == null ? "그 밖(다른 조건)" : reason.type() + " " + reason.excludedIngredients());
        return empty.withEmptyReason(reason);
    }

    /**
     * 후보 고르기에 쓰는 값.
     *
     * @param query        선택 재료 묶음을 채운 검색 조건
     * @param memberTastes 정렬에 쓸 회원 선호 맛 (원하는 맛을 말했으면 비고, 피할 맛은 뺐다)
     */
    private record Context(RecipeQuery query, RecipeCandidateScorer scorer, List<FridgeStock> fridge,
                           List<Member.Taste> memberTastes) {
    }

    /**
     * 단계마다 쌓이는 후보.
     *
     * @param query        키워드로 찾은 레시피가 어떻게 맞았는지(이름·재료 등)를 채운 검색 조건
     * @param knownAvoided 피할 맛 없이 다시 검색해서 새로 나온 레시피 (피할 맛 레시피로 본다)
     * @param refilled     피할 맛 없이 다시 검색해서 채운 레시피 수. 로그용
     * @param refillCalls  피할 맛 다시 검색의 ES 호출 수. 로그용
     */
    private record Collected(CandidatePool pool, PoolDetails details, RecipeQuery query, Set<Long> knownAvoided,
                             int refilled, int refillCalls) {
    }

    // 모은 후보로 키워드 맞은 방식을 가른다 (정렬은 하지 않는다)
    private static Collected classified(Context context, CandidatePool pool, PoolDetails details) {
        RecipeQuery query = context.query().withKeywordMatches(
                classifyKeywordHits(context.query(), pool.keywordHits(), pool.fragments(), pool.rows(), details.infos()));
        return new Collected(pool, details, query, Set.of(), 0, 0);
    }

    // 찾은 후보를 뒤에 합치고 새 레시피의 정보·맛을 읽어 맞은 방식을 다시 가른다. 로그용 경로·검색별 수는 뒤에 붙인다
    private Collected merge(Context context, Collected collected, CandidatePool found, String route, String counts) {
        CandidatePool pool = collected.pool();
        CandidatePool merged = pool.plus(found).withLog(pool.route() + route, pool.counts() + counts);
        return classified(context, merged,
                collected.details().plus(loadDetails(found, context.query(), context.memberTastes())));
    }

    private Context prepare(RecipeQuery request) {
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
        return new Context(grouped, scorer, fridge, memberTastes);
    }

    // 1. 모으기: ES(실패하면 DB) 경로로 모아 정보·맛을 읽고 키워드 맞은 방식을 가른다
    private Collected collect(Context context) {
        CandidatePool pool = recipeCandidateCollector.collectCandidates(context.query(), context.fridge(), context.scorer());
        return classified(context, pool, loadDetails(pool, context.query(), context.memberTastes()));
    }

    // 비슷한 요리를 더한 검색 조건과 후보
    private record Expanded(Context context, Collected collected) {
    }

    // 1-1. 비슷한 요리: ES 경로에서 요리 이름이 있고 그 이름 그대로 맞은 레시피(KeywordMatch NAME)가 없을 때만,
    // 비슷한 요리(캐시가 없으면 AI 호출 한 번)를 키워드 뒤에 더하고 글자로 찾아 후보에 더한다. 맞은 방식(늘린 이름)은 더한 키워드로 가른다.
    // 비슷한 요리가 없거나(AI 실패 포함) 이미 키워드에 있으면 그대로 둔다. "김치찌개"처럼 이름 그대로 찾히면 부르지 않는다
    private Expanded addSimilarDishes(Context context, Collected collected) {
        RecipeQuery query = context.query();
        if (!collected.pool().rowsNonBasicComplete() || query.keywordText() == null || hasNameMatch(collected)) {
            return new Expanded(context, collected);
        }
        Set<String> known = query.keywords().stream().map(ChatTexts::removeWhitespace).collect(Collectors.toSet());
        known.add(ChatTexts.removeWhitespace(query.keywordText()));
        List<String> similar = similarDishFinder.find(query.memberId(), query.keywordText()).stream()
                .filter(dish -> !known.contains(ChatTexts.removeWhitespace(dish)))
                .toList();
        if (similar.isEmpty()) {
            return new Expanded(context, collected);
        }
        RecipeQuery expandedQuery = query.withKeywords(Stream.concat(query.keywords().stream(), similar.stream()).toList());
        Context expandedContext = new Context(expandedQuery, context.scorer(), context.fridge(), context.memberTastes());
        CandidatePool found = recipeCandidateCollector.searchSimilarDishes(expandedQuery, similar, collected.pool().recipeIds());
        return new Expanded(expandedContext, merge(expandedContext, collected, found, "+비슷한 요리",
                ", 비슷한 요리 " + similar + " 새로 " + found.recipeIds().size()));
    }

    // 요리 이름 그대로 맞은 레시피(KeywordMatch NAME)가 있는지
    private static boolean hasNameMatch(Collected collected) {
        return collected.query().keywordMatches().values().stream()
                .anyMatch(match -> match.type() == KeywordMatch.Type.NAME);
    }

    // 2. 보충: ES 경로에서 키워드로 맞은(이름·늘린 이름·재료) 레시피가 하나도 없으면 레시피 검색으로 보충한다. 벡터 검색은 한 번만
    // ("탕짜면"처럼 ES가 글자 일부("짜")로만 걸어 온 레시피는 맞은 것으로 치지 않는다)
    // 우회: ES가 키워드로 표시한 결과만 믿지 않고 쪼개짐(SPLIT)을 빼고 세는 것은, 레시피 인덱스 분석기가 키워드를 한 글자까지
    // 쪼개 상관없는 레시피를 키워드 결과로 걸기 때문이다(탕짜면 → "짜"). 키워드 _name이 겹쳐 표시가 빠지던 문제는 #599로 고쳐졌다.
    // 분석기가 고쳐지면 ES 키워드 표시만으로 정하도록 걷어낸다
    private static boolean needsKeywordSupplement(Context context, Collected collected) {
        return collected.pool().rowsNonBasicComplete() && !context.query().keywords().isEmpty()
                && !anyStrong(collected.query().keywordMatches().values().stream());
    }

    private Collected supplementKeywords(Context context, Collected collected) {
        CandidatePool pool = collected.pool();
        CandidatePool supplement = recipeCandidateCollector.searchKeywordsOnce(context.query(), pool.recipeIds());
        String counts = ", 벡터 보충 새로 " + supplement.recipeIds().size()
                + (supplement.fragments().isEmpty() ? "" : " (이름 조각 " + supplement.fragments() + ")");
        // 새로 찾은 게 없어도 합친다 (벡터를 불렀는지 로그에 남기고, 이미 모은 후보를 다시 찾았으면 키워드 결과로 표시하려고)
        return merge(context, collected, supplement, "+벡터", counts);
    }

    // 2-1. 이름 조각: 키워드 보충을 하지 않았어도(비슷한 요리가 맞음) 요리 이름 그대로 맞은 레시피(KeywordMatch NAME)가 없으면
    // 요리 이름 조각만 글자로 찾는다 ("나가사키짬뽕" + 비슷한 요리 "짬뽕" → "나가사키"). ES 경로에서만, 벡터는 쓰지 않는다.
    // "김치찌개"처럼 이름 그대로 찾히면 하지 않는다. ES 호출은 분석 1번 + 조각마다 1번
    private Collected supplementDishFragments(Context context, Collected collected) {
        CandidatePool pool = collected.pool();
        if (!pool.rowsNonBasicComplete() || context.query().keywordText() == null || hasNameMatch(collected)) {
            return collected;
        }
        CandidatePool fragments = recipeCandidateCollector.searchDishFragmentsOnly(context.query(), pool.recipeIds());
        if (fragments.fragments().isEmpty()) {
            return collected;
        }
        return merge(context, collected, fragments, "+이름 조각",
                ", 이름 조각 " + fragments.fragments() + " 새로 " + fragments.recipeIds().size());
    }

    // 3. 피할 맛: ES 경로는 피할 맛을 검색에서 뺐으므로, 정렬해서 3개가 안 되면 피할 맛 없이 다시 검색한다
    private static boolean needsAvoidedRefill(Context context, Collected collected, RecipeCandidates ranked) {
        return collected.pool().rowsNonBasicComplete() && !context.query().avoidTastes().isEmpty()
                && ranked.candidates().size() < RecommendationPicker.PICK_COUNT;
    }

    // 새로 나온 레시피(피할 맛 레시피)를 뒤에 합쳐 knownAvoided로 표시한다. 새로 나온 게 없으면 ES 호출 수만 남긴다
    private Collected refillAvoided(Context context, Collected collected) {
        CandidatePool avoided = recipeCandidateCollector.searchAvoided(context.query(), context.fridge(),
                collected.pool().recipeIds());
        Collected merged = avoided.recipeIds().isEmpty() ? collected : merge(context, collected, avoided, "", "");
        return new Collected(merged.pool(), merged.details(), merged.query(), avoided.recipeIds(),
                avoided.recipeIds().size(), avoided.esCalls());
    }

    // 4. 로그: ES 호출은 전체 검색(ES 경로면 1)·요청만 검색·피할 맛 다시 검색, 벡터는 레시피 검색에서 벡터를 불렀는지
    private static void logCollected(Context context, Collected collected, RecipeCandidates ranked, long started) {
        CandidatePool pool = collected.pool();
        int fullCalls = Math.min(pool.esCalls(), 1);
        log.info("상담 후보 모으기 - memberId: {}, 경로: {}, 후보 {}건 ({}){}, 고른 후보 {}건, ES 호출 전체 {}·요청만 {}·피할 맛 재검색 {}, "
                        + "벡터 {}, {}ms",
                context.query().memberId(), pool.route(), pool.recipeIds().size(), pool.counts(),
                collected.refilled() > 0 ? ", 피할 맛 다시 채움 " + collected.refilled() + "건" : "",
                ranked.candidates().size(),
                fullCalls, pool.esCalls() - fullCalls, collected.refillCalls(), pool.vectorCalled() ? "호출" : "안 함",
                (System.nanoTime() - started) / 1_000_000);
    }

    // 5. 결과: 고른 후보에 부족한 재료와 후보의 맛을 채운다. 요리 이름을 못 찾았으면(dishNotFound) 못 찾음으로 표시한다
    private RecipeCandidates toResult(Context context, Collected collected, RecipeCandidates ranked, boolean dishNotFound) {
        CandidatePool pool = collected.pool();
        // 부족한 재료: ES 경로는 후보의 기본 양념 뺀 재료를 이미 다 읽었고, DB 경로는 고른 10개만 조회한다
        List<RecipeCandidateIngredient> nonBasicRows = pool.rowsNonBasicComplete()
                ? pool.rows()
                : recipeCandidateCollector.readNonBasicRows(ranked.candidates().stream().map(RecipeCandidate::recipeId).toList());
        RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(ranked, context.fridge(), nonBasicRows);
        // 요청 조건이 있는 추천에서 빈자리를 채울 때 반대 맛·매운 정도를 보도록 후보의 맛을 함께 넘긴다
        Map<Long, Set<Member.Taste>> candidateTastes = new HashMap<>();
        for (RecipeCandidate candidate : result.candidates()) {
            candidateTastes.put(candidate.recipeId(),
                    collected.details().tastes().getOrDefault(candidate.recipeId(), Set.of()));
        }
        return new RecipeCandidates(result.candidates(), result.fallback(), dishNotFound || collected.query().keywordsNotFound(),
                ranked.keptAvoidTastes())
                .withTasteDetails(candidateTastes, collected.details().spicy().maybe());
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
        TasteDetails tastes = recipeTasteReader.readForCandidates(recipeIds, pool.tasteLevels(),
                pool.rowsNonBasicComplete(), query, memberTastes);
        return new PoolDetails(infos, tastes.tastes(), tastes.spicy());
    }

    // 정렬할 후보. 요리 이름을 말했으면 키워드로 찾은 후보만 (정렬 상위 10개에서 자르기 전에 빼야 키워드 후보가 밀려 잘리지 않는다)
    private static Map<Long, RecipeCandidateInfo> rankableInfos(RecipeQuery query, Map<Long, RecipeCandidateInfo> infos) {
        if (!dishAsked(query)) {
            return infos;
        }
        Map<Long, RecipeCandidateInfo> kept = new HashMap<>(infos);
        kept.keySet().removeIf(recipeId -> !keywordFound(query.keywordMatches().get(recipeId)));
        log.debug("상담 후보 요리 이름을 말해 키워드로 찾은 후보만 정렬 - memberId: {}, 요리 이름: {}, 후보 {}건 중 {}건",
                query.memberId(), query.keywordText(), infos.size(), kept.size());
        return kept;
    }

    /**
     * 정보가 있는 레시피만 순위를 매긴다 (요리 이름을 말했으면 키워드로 찾은 후보만, rankableInfos).
     * 피할 맛인 레시피는 빼고 순위를 매긴다. 빼서 3개가 안 되면 그때만 피할 맛 레시피를 순위대로 뒤에 채운다 (요리 이름을 말한 추천은 채우지 않는다).
     * 피할 맛 레시피는 맛 데이터(확실히 매운 레시피면 SPICY가 들어 있다)로 보거나, 피할 맛 없이 다시 검색해서 새로 나온 것(knownAvoided)이다.
     * 피할 맛이 매운맛이면 확실히 매운 레시피에 더해 매울 수 있는 레시피(홍고추·청고추)까지 뺀다.
     * 단 사용자가 콕 집은 요리(요리 이름이 레시피 이름에 통째로 맞음, KeywordMatch NAME)는 피할 맛이어도 빼지 않고 남기며,
     * 남긴 레시피와 그 맛을 결과에 담아 카드 이유에 알린다 ("매운 거 말고 김치찌개 줘" → 김치찌개 카드 + "매운 요리예요").
     */
    private static RecipeCandidates rankPool(Context context, Collected collected) {
        RecipeQuery query = collected.query();
        PoolDetails details = collected.details();
        Map<Long, RecipeCandidateInfo> infos = rankableInfos(query, details.infos());
        ScoringContext scoring = ScoringContext.of(query, context.fridge(), context.memberTastes(), collected.pool().rows(),
                details.tastes());
        Function<Map<Long, RecipeCandidateInfo>, RecipeCandidates> rank =
                rankedInfos -> context.scorer().rank(scoring, rankedInfos, collected.pool().recipeIds());
        if (query.avoidTastes().isEmpty()) {
            return rank.apply(infos);
        }
        boolean avoidSpicy = query.avoidTastes().contains(Member.Taste.SPICY);
        Set<Long> avoided = infos.keySet().stream()
                .filter(id -> collected.knownAvoided().contains(id)
                        || details.tastes().getOrDefault(id, Set.of()).stream().anyMatch(query.avoidTastes()::contains)
                        || (avoidSpicy && details.spicy().maybe().contains(id)))
                .collect(Collectors.toSet());
        // 콕 집은 요리는 피할 맛보다 앞선다
        Map<Long, List<Member.Taste>> keptAvoid = new HashMap<>();
        for (Long id : avoided) {
            KeywordMatch match = query.keywordMatches().get(id);
            if (match != null && match.type() == KeywordMatch.Type.NAME) {
                keptAvoid.put(id, avoidedTastesOf(id, query, details));
            }
        }
        avoided.removeAll(keptAvoid.keySet());
        if (!keptAvoid.isEmpty()) {
            log.info("상담 후보 피할 맛이지만 콕 집은 요리라 남김 - 피할 맛: {}, 레시피: {}", query.avoidTastes(), keptAvoid);
        }
        Map<Long, RecipeCandidateInfo> kept = new HashMap<>(infos);
        kept.keySet().removeAll(avoided);
        RecipeCandidates ranked = rank.apply(kept).withKeptAvoidTastes(keptAvoid);
        log.debug("상담 후보 피할 맛 - 맛: {}, 뺀 레시피 수: {}, 남은 후보 수: {}", query.avoidTastes(), avoided.size(),
                ranked.candidates().size());
        // 요리 이름을 말한 추천은 1~2장이어도 그대로 보낸다 (피할 맛 레시피로 채우지 않는다. 콕 집은 요리는 위에서 이미 남겼다)
        if (ranked.candidates().size() >= RecommendationPicker.PICK_COUNT || avoided.isEmpty() || dishAsked(query)) {
            return ranked;
        }
        Map<Long, RecipeCandidateInfo> onlyAvoided = new HashMap<>(infos);
        onlyAvoided.keySet().retainAll(avoided);
        List<RecipeCandidate> refill = rank.apply(onlyAvoided).candidates();
        return new RecipeCandidates(Stream.concat(ranked.candidates().stream(), refill.stream())
                .limit(RecipeCandidateScorer.LIMIT)
                .toList(), ranked.fallback(), false, keptAvoid);
    }

    // 콕 집어 남긴 레시피가 어떤 피할 맛인지. 맛 데이터·매운 재료로 모르면(피할 맛 없이 다시 검색해서 찾음) 피할 맛 전체
    private static List<Member.Taste> avoidedTastesOf(Long id, RecipeQuery query, PoolDetails details) {
        List<Member.Taste> tastes = query.avoidTastes().stream()
                .filter(taste -> details.tastes().getOrDefault(id, Set.of()).contains(taste)
                        || (taste == Member.Taste.SPICY && details.spicy().maybe().contains(id)))
                .toList();
        return tastes.isEmpty() ? query.avoidTastes() : tastes;
    }

    // 키워드로 찾은 레시피가 이름·늘린 이름·재료·이름 조각·쪼개짐·벡터 중 어떻게 맞았는지 가른다 (레시피 정보·재료는 후보 전체를 읽은 것을 쓴다)
    private static Map<Long, KeywordMatch> classifyKeywordHits(
            RecipeQuery query,
            Map<Long, Boolean> keywordHits,
            List<String> fragments,
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
                matches.put(recipeId, KeywordMatchClassifier.classify(query.keywordText(), query.keywords(), fragments,
                        vectorOnly, info.name(), ingredientNames.getOrDefault(recipeId, List.of())));
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

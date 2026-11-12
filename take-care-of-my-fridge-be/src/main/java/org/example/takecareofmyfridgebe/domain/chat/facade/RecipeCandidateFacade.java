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

    /**
     * 회원 냉장고와 선택 재료·선호 맛·임박 재료로 레시피 후보를 최대 10개 고른다.
     *
     * @param request 회원 ID, 선택 재료, 의도 판단에서 뽑은 요청 조건 (선택 재료 묶음은 여기서 채운다)
     * @return 정렬된 후보와 fallback 여부 (선택 재료가 없거나 하나도 겹치지 않으면 true)
     */
    public RecipeCandidates findCandidates(RecipeQuery request) {
        long started = System.nanoTime();
        Context context = prepare(request);
        Collected collected = collect(context);
        collected = supplementKeywords(context, collected);
        collected = refillAvoided(context, collected);
        logCollected(context, collected, started);
        return toResult(context, collected);
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
     * @param refilled    피할 맛 없이 다시 검색해서 채운 레시피 수. 로그용
     * @param refillCalls 피할 맛 다시 검색의 ES 호출 수. 로그용
     */
    private record Collected(CandidatePool pool, PoolDetails details, Ranked ranked, int refilled, int refillCalls) {
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

    // 1. 모으기: ES(실패하면 DB) 경로로 모아 정보·맛을 읽고 정렬한다
    private Collected collect(Context context) {
        CandidatePool pool = recipeCandidateCollector.collectCandidates(context.query(), context.fridge(), context.scorer());
        PoolDetails details = loadDetails(pool, context.query(), context.memberTastes());
        return new Collected(pool, details, rankPool(context, pool, details, Set.of()), 0, 0);
    }

    // 2. 보충: ES 경로에서 키워드로 맞은(이름·늘린 이름·재료) 레시피가 하나도 없으면 레시피 검색으로 보충한다. 벡터 검색은 한 번만
    // ("탕짜면"처럼 ES가 글자 일부("짜")로만 걸어 온 레시피는 맞은 것으로 치지 않는다)
    // 우회: ES가 키워드로 표시한 결과만 믿지 않고 쪼개짐(SPLIT)을 빼고 세는 것은, 레시피 인덱스 분석기가 키워드를 한 글자까지
    // 쪼개 상관없는 레시피를 키워드 결과로 걸기 때문이다(탕짜면 → "짜"). 키워드 _name이 겹쳐 표시가 빠지던 문제는 #599로 고쳐졌다.
    // 분석기가 고쳐지면 ES 키워드 표시만으로 정하도록 걷어낸다
    private Collected supplementKeywords(Context context, Collected collected) {
        CandidatePool pool = collected.pool();
        RecipeQuery query = context.query();
        if (!pool.rowsNonBasicComplete() || query.keywords().isEmpty()
                || hasStrongKeywordMatch(collected.ranked().query())) {
            return collected;
        }
        CandidatePool supplement = recipeCandidateCollector.searchKeywordsOnce(query, pool.recipeIds());
        String route = pool.route() + "+벡터";
        String counts = pool.counts() + ", 벡터 보충 새로 " + supplement.recipeIds().size();
        // 새로 찾은 게 없어도 합친다 (벡터를 불렀는지 로그에 남기려고)
        CandidatePool merged = pool.plus(supplement);
        PoolDetails details = collected.details();
        Ranked ranked = collected.ranked();
        if (!supplement.recipeIds().isEmpty()) {
            details = details.plus(loadDetails(supplement, query, context.memberTastes()));
            ranked = rankPool(context, merged, details, Set.of());
        }
        return new Collected(merged.withLog(route, counts), details, ranked, collected.refilled(), collected.refillCalls());
    }

    // 3. 피할 맛: ES 경로는 피할 맛을 검색에서 뺐으므로, 3개가 안 되면 피할 맛 없이 다시 검색해 새로 나온 레시피(피할 맛 레시피)를 뒤에 채운다
    private Collected refillAvoided(Context context, Collected collected) {
        CandidatePool pool = collected.pool();
        RecipeQuery query = context.query();
        if (!pool.rowsNonBasicComplete() || query.avoidTastes().isEmpty()
                || collected.ranked().candidates().candidates().size() >= RecommendationPicker.PICK_COUNT) {
            return collected;
        }
        CandidatePool avoided = recipeCandidateCollector.searchAvoided(query, context.fridge(), pool.recipeIds());
        if (avoided.recipeIds().isEmpty()) {
            return new Collected(pool, collected.details(), collected.ranked(), collected.refilled(), avoided.esCalls());
        }
        CandidatePool merged = pool.plus(avoided);
        PoolDetails details = collected.details().plus(loadDetails(avoided, query, context.memberTastes()));
        Ranked ranked = rankPool(context, merged, details, avoided.recipeIds());
        return new Collected(merged, details, ranked, avoided.recipeIds().size(), avoided.esCalls());
    }

    // 4. 로그: ES 호출은 전체 검색(ES 경로면 1)·요청만 검색·피할 맛 다시 검색, 벡터는 레시피 검색에서 벡터를 불렀는지
    private static void logCollected(Context context, Collected collected, long started) {
        CandidatePool pool = collected.pool();
        int fullCalls = Math.min(pool.esCalls(), 1);
        log.info("상담 후보 모으기 - memberId: {}, 경로: {}, 후보 {}건 ({}){}, 고른 후보 {}건, ES 호출 전체 {}·요청만 {}·피할 맛 재검색 {}, "
                        + "벡터 {}, {}ms",
                context.query().memberId(), pool.route(), pool.recipeIds().size(), pool.counts(),
                collected.refilled() > 0 ? ", 피할 맛 다시 채움 " + collected.refilled() + "건" : "",
                collected.ranked().candidates().candidates().size(),
                fullCalls, pool.esCalls() - fullCalls, collected.refillCalls(), pool.vectorCalled() ? "호출" : "안 함",
                (System.nanoTime() - started) / 1_000_000);
    }

    // 5. 결과: 고른 후보에 부족한 재료와 후보의 맛을 채운다
    private RecipeCandidates toResult(Context context, Collected collected) {
        CandidatePool pool = collected.pool();
        Ranked ranked = collected.ranked();
        // 부족한 재료: ES 경로는 후보의 기본 양념 뺀 재료를 이미 다 읽었고, DB 경로는 고른 10개만 조회한다
        List<RecipeCandidateIngredient> nonBasicRows = pool.rowsNonBasicComplete()
                ? pool.rows()
                : recipeCandidateCollector.readNonBasicRows(
                ranked.candidates().candidates().stream().map(RecipeCandidate::recipeId).toList());
        RecipeCandidates result = RecipeCandidateScorer.withMissingIngredients(ranked.candidates(), context.fridge(),
                nonBasicRows);
        // 요청 조건이 있는 추천에서 빈자리를 채울 때 반대 맛·매운 정도를 보도록 후보의 맛을 함께 넘긴다
        Map<Long, Set<Member.Taste>> candidateTastes = new HashMap<>();
        for (RecipeCandidate candidate : result.candidates()) {
            candidateTastes.put(candidate.recipeId(),
                    collected.details().tastes().getOrDefault(candidate.recipeId(), Set.of()));
        }
        return new RecipeCandidates(result.candidates(), result.fallback(), ranked.query().keywordsNotFound(),
                ranked.candidates().keptAvoidTastes())
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

    // 정렬한 후보와, 키워드 결과(이름·재료 등으로 맞은 방식)를 채운 검색 조건
    private record Ranked(RecipeQuery query, RecipeCandidates candidates) {
    }

    private static Ranked rankPool(Context context, CandidatePool pool, PoolDetails details, Set<Long> knownAvoided) {
        RecipeQuery query = context.query().withKeywordMatches(
                classifyKeywordHits(context.query(), pool.keywordHits(), pool.rows(), details.infos()));
        RankInput input = new RankInput(query, context.fridge(), context.memberTastes(), pool.rows(), details.infos(),
                details.tastes(), details.spicy(), knownAvoided, pool.recipeIds());
        return new Ranked(query, rankAvoiding(context.scorer(), input));
    }

    // 키워드로 이름·늘린 이름·재료가 맞은 레시피가 있는지 (쪼개짐·벡터는 맞은 것으로 치지 않는다)
    private static boolean hasStrongKeywordMatch(RecipeQuery query) {
        return query.keywordMatches().values().stream().anyMatch(KeywordMatch::strong);
    }

    /**
     * 정렬에 넣는 값.
     *
     * @param recipeTastes 레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
     * @param knownAvoided 검색에서 피할 맛 레시피로 판단한 레시피 (피할 맛 없이 다시 검색해서 새로 나온 것)
     * @param candidateIds 후보 레시피 ID
     */
    private record RankInput(
            RecipeQuery query,
            List<FridgeStock> fridge,
            List<Member.Taste> memberTastes,
            List<RecipeCandidateIngredient> rows,
            Map<Long, RecipeCandidateInfo> infos,
            Map<Long, Set<Member.Taste>> recipeTastes,
            SpicyRecipes spicy,
            Set<Long> knownAvoided,
            Set<Long> candidateIds
    ) {
        // 정보가 있는 레시피만 정렬한다 (infos로 피할 맛 레시피를 빼거나 그것만 남긴다)
        RecipeCandidates rank(RecipeCandidateScorer scorer, Map<Long, RecipeCandidateInfo> rankedInfos) {
            return scorer.rank(query, fridge, memberTastes, rows, rankedInfos, recipeTastes, candidateIds);
        }
    }

    /**
     * 피할 맛인 레시피는 빼고 순위를 매긴다. 빼서 3개가 안 되면 그때만 피할 맛 레시피를 순위대로 뒤에 채운다.
     * 피할 맛이 매운맛이면 확실히 매운 레시피에 더해 매울 수 있는 레시피(홍고추·청고추)까지 뺀다.
     * 단 사용자가 콕 집은 요리(요리 이름이 레시피 이름에 통째로 맞음, KeywordMatch NAME)는 피할 맛이어도 빼지 않고 남기며,
     * 남긴 레시피와 그 맛을 결과에 담아 카드 이유에 알린다 ("매운 거 말고 김치찌개 줘" → 김치찌개 카드 + "매운 요리예요").
     */
    private static RecipeCandidates rankAvoiding(RecipeCandidateScorer scorer, RankInput input) {
        RecipeQuery query = input.query();
        if (query.avoidTastes().isEmpty()) {
            return input.rank(scorer, input.infos());
        }
        boolean avoidSpicy = query.avoidTastes().contains(Member.Taste.SPICY);
        Set<Long> avoided = input.infos().keySet().stream()
                .filter(id -> input.knownAvoided().contains(id)
                        || input.recipeTastes().getOrDefault(id, Set.of()).stream().anyMatch(query.avoidTastes()::contains)
                        || (avoidSpicy && input.spicy().maybe().contains(id)))
                .collect(Collectors.toSet());
        // 콕 집은 요리는 피할 맛보다 앞선다
        Map<Long, List<Member.Taste>> keptAvoid = new HashMap<>();
        for (Long id : avoided) {
            KeywordMatch match = query.keywordMatches().get(id);
            if (match != null && match.type() == KeywordMatch.Type.NAME) {
                keptAvoid.put(id, avoidedTastesOf(id, input));
            }
        }
        avoided.removeAll(keptAvoid.keySet());
        if (!keptAvoid.isEmpty()) {
            log.info("상담 후보 피할 맛이지만 콕 집은 요리라 남김 - 피할 맛: {}, 레시피: {}", query.avoidTastes(), keptAvoid);
        }
        Map<Long, RecipeCandidateInfo> kept = new HashMap<>(input.infos());
        kept.keySet().removeAll(avoided);
        RecipeCandidates ranked = input.rank(scorer, kept).withKeptAvoidTastes(keptAvoid);
        log.debug("상담 후보 피할 맛 - 맛: {}, 뺀 레시피 수: {}, 남은 후보 수: {}", query.avoidTastes(), avoided.size(),
                ranked.candidates().size());
        if (ranked.candidates().size() >= RecommendationPicker.PICK_COUNT || avoided.isEmpty()) {
            return ranked;
        }
        Map<Long, RecipeCandidateInfo> onlyAvoided = new HashMap<>(input.infos());
        onlyAvoided.keySet().retainAll(avoided);
        List<RecipeCandidate> refill = input.rank(scorer, onlyAvoided).candidates();
        return new RecipeCandidates(Stream.concat(ranked.candidates().stream(), refill.stream())
                .limit(RecipeCandidateScorer.LIMIT)
                .toList(), ranked.fallback(), false, keptAvoid);
    }

    // 콕 집어 남긴 레시피가 어떤 피할 맛인지. 맛 데이터·매운 재료로 모르면(피할 맛 없이 다시 검색해서 찾음) 피할 맛 전체
    private static List<Member.Taste> avoidedTastesOf(Long id, RankInput input) {
        List<Member.Taste> tastes = input.query().avoidTastes().stream()
                .filter(taste -> input.recipeTastes().getOrDefault(id, Set.of()).contains(taste)
                        || (taste == Member.Taste.SPICY && input.spicy().maybe().contains(id)))
                .toList();
        return tastes.isEmpty() ? input.query().avoidTastes() : tastes;
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

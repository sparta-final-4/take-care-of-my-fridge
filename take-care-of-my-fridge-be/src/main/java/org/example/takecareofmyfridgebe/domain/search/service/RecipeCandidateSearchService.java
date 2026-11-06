package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSpicyLevel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * AI 상담 레시피 후보 검색 (ES). 조건에 맞는 레시피를 최대 100건 고르고, 어떤 조건에 맞았는지(matched)를 함께 돌려준다.
 * 정렬과 카드 구성은 호출하는 쪽(chat)이 한다.
 * <ul>
 *   <li>should(점수): 분류·키워드·선택 재료·임박 재료·냉장고 재료·원하는 맛. 하나 이상 맞아야 한다</li>
 *   <li>must_not: 제외 분류·뺄 재료·피할 맛·직전 추천 레시피</li>
 *   <li>filter: 최대 조리 시간 (조리 시간이 없는 레시피는 통과)</li>
 * </ul>
 * 피할 맛 때문에 후보가 모자라면 호출하는 쪽이 피할 맛 없이 다시 검색한다 ({@link Condition#withoutAvoidTastes()}).
 */
@Service
@RequiredArgsConstructor
public class RecipeCandidateSearchService {

    // matched에 담기는 조건 이름
    public static final String MATCH_CATEGORY = "category";
    public static final String MATCH_KEYWORD = "keyword";
    public static final String MATCH_SELECTED = "selected";
    public static final String MATCH_IMMINENT = "imminent";
    public static final String MATCH_FRIDGE = "fridge";
    public static final String MATCH_TASTE = "taste";

    static final int POOL_SIZE = 100;

    // 요청에 직접 맞는 것(키워드·분류·선택 재료)이 냉장고 재료보다 앞서도록 가중치를 준다
    private static final float KEYWORD_BOOST = 4.0f;
    private static final float CATEGORY_BOOST = 3.0f;
    private static final float SELECTED_BOOST = 3.0f;
    private static final float IMMINENT_BOOST = 2.0f;
    private static final float FRIDGE_BOOST = 1.0f;
    private static final float TASTE_BOOST = 1.0f;

    private static final List<String> KEYWORD_FIELDS = List.of("name^3", "ingredients.text^2", "summary");
    // 피할 맛이 매운맛이면 확실히 매운 레시피에 더해 매울 수 있는 레시피까지 뺀다
    private static final List<String> MAYBE_SPICY = List.of(RecipeSpicyLevel.MILD.name(), RecipeSpicyLevel.HOT.name());

    private final ElasticsearchClient esClient;
    private final RecipeSearchProperties properties;

    /**
     * @return 점수 순 후보. 점수를 낼 조건(should)이 하나도 없으면 검색하지 않고 빈 목록
     */
    public List<CandidateHit> search(Condition condition) throws IOException {
        List<Query> shoulds = shouldQueries(condition);
        if (shoulds.isEmpty()) {
            return List.of();
        }
        return esClient.search(searchRequest(condition, shoulds), Void.class).hits().hits().stream()
                .map(hit -> new CandidateHit(
                        Long.valueOf(hit.id()),
                        hit.score() == null ? 0 : hit.score(),
                        hit.matchedQueries().keySet()))
                .toList();
    }

    SearchRequest searchRequest(Condition condition, List<Query> shoulds) {
        List<Query> mustNots = mustNotQueries(condition);
        return SearchRequest.of(s -> s
                .index(properties.indexProperties().recipe().alias())
                .size(POOL_SIZE)
                .source(src -> src.fetch(false))
                .query(q -> q.bool(b -> {
                    if (condition.selectedOnly() && !condition.selectedIngredientIds().isEmpty()) {
                        b.must(idTerms("ingredient_ids", condition.selectedIngredientIds()));
                    }
                    if (condition.maxMinutes() != null) {
                        b.filter(maxMinutesQuery(condition.maxMinutes()));
                    }
                    return b.mustNot(mustNots)
                            .should(shoulds)
                            .minimumShouldMatch("1");
                })));
    }

    List<Query> shouldQueries(Condition condition) {
        List<Query> shoulds = new ArrayList<>();
        if (!condition.categories().isEmpty()) {
            shoulds.add(named(stringTerms("category", condition.categories()), CATEGORY_BOOST, MATCH_CATEGORY));
        }
        if (!condition.keywords().isEmpty()) {
            List<Query> keywords = condition.keywords().stream()
                    .map(RecipeCandidateSearchService::keywordQuery)
                    .toList();
            shoulds.add(Query.of(q -> q.bool(b -> b.should(keywords).boost(KEYWORD_BOOST).queryName(MATCH_KEYWORD))));
        }
        addEachIngredient(shoulds, condition.selectedIngredientIds(), SELECTED_BOOST, MATCH_SELECTED);
        addEachIngredient(shoulds, condition.imminentIngredientIds(), IMMINENT_BOOST, MATCH_IMMINENT);
        addEachIngredient(shoulds, condition.fridgeIngredientIds(), FRIDGE_BOOST, MATCH_FRIDGE);
        if (!condition.wantedTastes().isEmpty()) {
            shoulds.add(named(stringTerms("tastes", names(condition.wantedTastes())), TASTE_BOOST, MATCH_TASTE));
        }
        return shoulds;
    }

    private static List<Query> mustNotQueries(Condition condition) {
        List<Query> mustNots = new ArrayList<>();
        if (!condition.excludeCategories().isEmpty()) {
            mustNots.add(stringTerms("category", condition.excludeCategories()));
        }
        if (!condition.excludeIngredientIds().isEmpty()) {
            mustNots.add(idTerms("ingredient_ids", condition.excludeIngredientIds()));
        }
        if (!condition.avoidTastes().isEmpty()) {
            mustNots.add(stringTerms("tastes", names(condition.avoidTastes())));
        }
        if (condition.avoidTastes().contains(Member.Taste.SPICY)) {
            mustNots.add(stringTerms("spicy_level", MAYBE_SPICY));
        }
        if (!condition.excludeRecipeIds().isEmpty()) {
            List<String> ids = condition.excludeRecipeIds().stream().map(String::valueOf).toList();
            mustNots.add(Query.of(q -> q.ids(i -> i.values(ids))));
        }
        return mustNots;
    }

    // 키워드 하나의 단어가 이름·재료·요약 중 어딘가에 모두 있어야 한다 (레시피 검색과 같은 기준)
    private static Query keywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields(KEYWORD_FIELDS)
                .type(TextQueryType.CrossFields)
                .operator(Operator.And)));
    }

    // terms는 몇 개가 맞아도 점수가 같아서, 재료마다 term을 걸어 많이 겹칠수록 점수가 오르게 한다
    private static void addEachIngredient(List<Query> shoulds, Collection<Long> ids, float boost, String name) {
        if (ids.isEmpty()) {
            return;
        }
        List<Query> terms = ids.stream()
                .map(id -> Query.of(q -> q.term(t -> t.field("ingredient_ids").value(id))))
                .toList();
        shoulds.add(Query.of(q -> q.bool(b -> b.should(terms).boost(boost).queryName(name))));
    }

    // cook_time_min <= maxMinutes 또는 조리 시간이 없는 레시피
    private static Query maxMinutesQuery(int maxMinutes) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.range(r -> r.number(n -> n.field("cook_time_min").lte((double) maxMinutes))))
                .should(s -> s.bool(n -> n.mustNot(m -> m.exists(e -> e.field("cook_time_min")))))
                .minimumShouldMatch("1")));
    }

    private static Query named(Query query, float boost, String name) {
        return Query.of(q -> q.bool(b -> b.must(query).boost(boost).queryName(name)));
    }

    private static Query idTerms(String field, Collection<Long> ids) {
        List<FieldValue> values = ids.stream().map(id -> FieldValue.of(id.longValue())).toList();
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(values))));
    }

    private static Query stringTerms(String field, Collection<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(fieldValues))));
    }

    private static List<String> names(Collection<Member.Taste> tastes) {
        return tastes.stream().map(Enum::name).toList();
    }

    /**
     * 후보 검색 조건. null인 목록은 빈 목록으로 본다.
     *
     * @param fridgeIngredientIds   냉장고에 있는 재료 id (점수)
     * @param imminentIngredientIds 소비기한이 임박한 재료 id (점수)
     * @param selectedIngredientIds 사용자가 고른 재료 id (점수, selectedOnly면 하나 이상 꼭 들어 있어야 한다)
     * @param selectedOnly          고른 재료가 들어간 레시피만 찾을지
     * @param categories            원하는 레시피 분류 (점수)
     * @param keywords              요리 이름 등 검색어. 하나씩 따로 맞춰 본다 (점수)
     * @param wantedTastes          원하는 맛 (점수)
     * @param avoidTastes           피할 맛 (제외)
     * @param maxMinutes            최대 조리 시간. null이면 조건 없음
     * @param excludeIngredientIds  뺄 재료 id (제외)
     * @param excludeCategories     뺄 레시피 분류 (제외)
     * @param excludeRecipeIds      직전에 추천한 레시피 id (제외)
     */
    public record Condition(
            List<Long> fridgeIngredientIds,
            List<Long> imminentIngredientIds,
            List<Long> selectedIngredientIds,
            boolean selectedOnly,
            List<String> categories,
            List<String> keywords,
            List<Member.Taste> wantedTastes,
            List<Member.Taste> avoidTastes,
            Integer maxMinutes,
            List<Long> excludeIngredientIds,
            List<String> excludeCategories,
            Set<Long> excludeRecipeIds
    ) {
        public Condition {
            fridgeIngredientIds = fridgeIngredientIds == null ? List.of() : List.copyOf(fridgeIngredientIds);
            imminentIngredientIds = imminentIngredientIds == null ? List.of() : List.copyOf(imminentIngredientIds);
            selectedIngredientIds = selectedIngredientIds == null ? List.of() : List.copyOf(selectedIngredientIds);
            categories = categories == null ? List.of() : List.copyOf(categories);
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
            wantedTastes = wantedTastes == null ? List.of() : List.copyOf(wantedTastes);
            avoidTastes = avoidTastes == null ? List.of() : List.copyOf(avoidTastes);
            excludeIngredientIds = excludeIngredientIds == null ? List.of() : List.copyOf(excludeIngredientIds);
            excludeCategories = excludeCategories == null ? List.of() : List.copyOf(excludeCategories);
            excludeRecipeIds = excludeRecipeIds == null ? Set.of() : Set.copyOf(excludeRecipeIds);
        }

        // 피할 맛을 빼서 후보가 모자랄 때 다시 검색하는 조건
        public Condition withoutAvoidTastes() {
            return new Condition(fridgeIngredientIds, imminentIngredientIds, selectedIngredientIds, selectedOnly,
                    categories, keywords, wantedTastes, List.of(), maxMinutes, excludeIngredientIds,
                    excludeCategories, excludeRecipeIds);
        }
    }

    /**
     * @param matched 맞은 조건 이름 (MATCH_* 상수)
     */
    public record CandidateHit(Long recipeId, double score, Set<String> matched) {
        public CandidateHit {
            matched = Set.copyOf(matched);
        }

        public boolean matched(String name) {
            return matched.contains(name);
        }
    }
}

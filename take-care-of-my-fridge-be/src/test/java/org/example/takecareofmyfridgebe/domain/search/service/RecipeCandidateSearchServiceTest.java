package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.CandidateHit;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecipeCandidateSearchServiceTest {

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);
    private final RecipeCandidateSearchService service = new RecipeCandidateSearchService(esClient,
            new RecipeSearchProperties(
                    SearchEngine.ELASTICSEARCH,
                    new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500, false), null, null),
                    null));

    private static Condition condition(List<Long> fridge, List<Long> selected, boolean selectedOnly,
                                       List<Member.Taste> wanted, List<Member.Taste> avoid, Integer maxMinutes) {
        return new Condition(fridge, null, selected, selectedOnly, null, null, wanted, avoid, maxMinutes,
                null, null, null);
    }

    private BoolQuery bool(Condition condition) {
        return service.searchRequest(condition, service.shouldQueries(condition)).query().bool();
    }

    private static List<String> termsValues(Query query) {
        return query.terms().terms().value().stream()
                .map(value -> value.isString() ? value.stringValue() : String.valueOf(value.longValue()))
                .toList();
    }

    @Test
    @DisplayName("점수를 낼 조건이 하나도 없으면 ES를 부르지 않고 빈 목록을 돌려준다")
    void returnsEmpty_whenNoShouldCondition() throws IOException {
        // Given
        Condition condition = condition(null, null, false, null, List.of(Member.Taste.SPICY), 30);

        // When
        List<CandidateHit> hits = service.search(condition);

        // Then
        assertThat(hits).isEmpty();
        verifyNoInteractions(esClient);
    }

    @Test
    @DisplayName("100건까지 문서 본문 없이 찾고, 조건 중 하나 이상 맞아야 한다")
    void requestsPoolWithoutSource() {
        // Given
        Condition condition = condition(List.of(1L), null, false, null, null, null);

        // When
        SearchRequest request = service.searchRequest(condition, service.shouldQueries(condition));

        // Then
        assertThat(request.index()).containsExactly("recipes");
        assertThat(request.size()).isEqualTo(100);
        assertThat(request.source().fetch()).isFalse();
        assertThat(request.query().bool().minimumShouldMatch()).isEqualTo("1");
    }

    @Test
    @DisplayName("분류·키워드·선택·임박·냉장고 재료·원하는 맛을 이름 붙인 should로 건다")
    void addsNamedShoulds_whenEveryScoreConditionGiven() {
        // Given
        Condition condition = new Condition(List.of(1L, 2L), List.of(3L), List.of(4L), false, List.of("찌개"),
                List.of("김치찌개", "된장찌개"), List.of(Member.Taste.SPICY), null, null, null, null, null);

        // When
        List<Query> shoulds = service.shouldQueries(condition);

        // Then
        assertThat(shoulds).extracting(this::queryName).containsExactly(
                "category", "keyword", "keyword", "selected", "imminent", "fridge", "taste");
        assertThat(shoulds.get(1).multiMatch().query()).isEqualTo("김치찌개");
        assertThat(shoulds.get(1).multiMatch().boost()).isEqualTo(4.0f);
        assertThat(termsValues(shoulds.get(6).bool().must().get(0))).containsExactly("SPICY");
    }

    @Test
    @DisplayName("재료 조건은 재료마다 term을 따로 걸어 많이 겹칠수록 점수가 오르게 한다")
    void addsTermPerIngredient() {
        // Given
        Condition condition = condition(List.of(1L, 2L, 3L), null, false, null, null, null);

        // When
        Query fridge = service.shouldQueries(condition).get(0);

        // Then
        assertThat(fridge.bool().queryName()).isEqualTo("fridge");
        assertThat(fridge.bool().boost()).isEqualTo(1.0f);
        assertThat(fridge.bool().should()).extracting(q -> q.term().value().longValue()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("선택 재료만 쓰면 선택 재료가 하나 이상 들어 있어야 한다")
    void addsSelectedMust_whenSelectedOnly() {
        // Given
        Condition condition = condition(null, List.of(4L, 5L), true, null, null, null);

        // When
        BoolQuery bool = bool(condition);

        // Then
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().get(0).terms().field()).isEqualTo("ingredient_ids");
        assertThat(termsValues(bool.must().get(0))).containsExactly("4", "5");
    }

    @Test
    @DisplayName("선택 재료만 쓰지 않으면 must를 걸지 않는다")
    void skipsSelectedMust_whenNotSelectedOnly() {
        // Given
        Condition condition = condition(null, List.of(4L), false, null, null, null);

        // When
        BoolQuery bool = bool(condition);

        // Then
        assertThat(bool.must()).isEmpty();
    }

    @Test
    @DisplayName("뺄 분류·뺄 재료·직전 추천 레시피는 must_not으로 뺀다")
    void addsMustNots_whenExcludeConditionsGiven() {
        // Given
        Condition condition = new Condition(List.of(1L), null, null, false, null, null, null, null, null,
                List.of(7L), List.of("양념", "음료"), Set.of(99L));

        // When
        List<Query> mustNots = bool(condition).mustNot();

        // Then
        assertThat(mustNots).hasSize(3);
        assertThat(mustNots.get(0).terms().field()).isEqualTo("category");
        assertThat(termsValues(mustNots.get(0))).containsExactly("양념", "음료");
        assertThat(mustNots.get(1).terms().field()).isEqualTo("ingredient_ids");
        assertThat(mustNots.get(2).ids().values()).containsExactly("99");
    }

    @Test
    @DisplayName("피할 맛이 매운맛이면 맛 SPICY와 매울 수 있는 레시피(MILD·HOT)까지 뺀다")
    void excludesMaybeSpicy_whenAvoidSpicy() {
        // Given
        Condition condition = condition(List.of(1L), null, false, null, List.of(Member.Taste.SPICY), null);

        // When
        List<Query> mustNots = bool(condition).mustNot();

        // Then
        assertThat(mustNots).hasSize(2);
        assertThat(mustNots.get(0).terms().field()).isEqualTo("tastes");
        assertThat(termsValues(mustNots.get(0))).containsExactly("SPICY");
        assertThat(mustNots.get(1).terms().field()).isEqualTo("spicy_level");
        assertThat(termsValues(mustNots.get(1))).containsExactly("MILD", "HOT");
    }

    @Test
    @DisplayName("피할 맛이 매운맛이 아니면 spicy_level은 보지 않는다")
    void skipsSpicyLevel_whenAvoidOtherTaste() {
        // Given
        Condition condition = condition(List.of(1L), null, false, null, List.of(Member.Taste.SWEET), null);

        // When
        List<Query> mustNots = bool(condition).mustNot();

        // Then
        assertThat(mustNots).hasSize(1);
        assertThat(termsValues(mustNots.get(0))).containsExactly("SWEET");
    }

    @Test
    @DisplayName("최대 조리 시간은 그 시간 이하이거나 조리 시간이 없는 레시피를 통과시킨다")
    void filtersMaxMinutesOrMissing() {
        // Given
        Condition condition = condition(List.of(1L), null, false, null, null, 20);

        // When
        List<Query> filters = bool(condition).filter();

        // Then
        assertThat(filters).hasSize(1);
        List<Query> either = filters.get(0).bool().should();
        assertThat(either.get(0).range().number().field()).isEqualTo("cook_time_min");
        assertThat(either.get(0).range().number().lte()).isEqualTo(20.0);
        assertThat(either.get(1).bool().mustNot().get(0).exists().field()).isEqualTo("cook_time_min");
        assertThat(filters.get(0).bool().minimumShouldMatch()).isEqualTo("1");
    }

    @Test
    @DisplayName("피할 맛 없이 다시 검색하는 조건은 피할 맛만 비우고 나머지는 그대로 둔다")
    void clearsOnlyAvoidTastes_whenWithoutAvoidTastes() {
        // Given
        Condition condition = new Condition(List.of(1L), List.of(2L), List.of(3L), true, List.of("찌개"),
                List.of("김치찌개"), List.of(Member.Taste.SALTY), List.of(Member.Taste.SPICY), 30,
                List.of(4L), List.of("양념"), Set.of(5L));

        // When
        Condition retry = condition.withoutAvoidTastes();

        // Then
        assertThat(retry.avoidTastes()).isEmpty();
        assertThat(retry).isEqualTo(new Condition(List.of(1L), List.of(2L), List.of(3L), true, List.of("찌개"),
                List.of("김치찌개"), List.of(Member.Taste.SALTY), List.of(), 30,
                List.of(4L), List.of("양념"), Set.of(5L)));
    }

    @Test
    @DisplayName("검색 결과를 레시피 id·점수·맞은 조건 이름으로 바꾼다")
    void returnsHitsWithMatchedQueries() throws IOException {
        // Given
        SearchResponse<Void> response = SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(2).relation(TotalHitsRelation.Eq))
                        .hits(List.of(
                                Hit.<Void>of(hit -> hit.index("recipes_1").id("10").score(5.0)
                                        .matchedQueries(Map.of("selected", 3.0, "fridge", 2.0))),
                                Hit.<Void>of(hit -> hit.index("recipes_1").id("20").score(1.0)
                                        .matchedQueries(Map.of("fridge", 1.0)))))));
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response);

        // When
        List<CandidateHit> hits = service.search(condition(List.of(1L), List.of(2L), false, null, null, null));

        // Then
        assertThat(hits).extracting(CandidateHit::recipeId).containsExactly(10L, 20L);
        assertThat(hits.get(0).score()).isEqualTo(5.0);
        assertThat(hits.get(0).matched(RecipeCandidateSearchService.MATCH_SELECTED)).isTrue();
        assertThat(hits.get(1).matched(RecipeCandidateSearchService.MATCH_SELECTED)).isFalse();
        assertThat(hits.get(1).matched()).containsExactly("fridge");
    }

    private String queryName(Query query) {
        return query.isMultiMatch() ? query.multiMatch().queryName() : query.bool().queryName();
    }
}

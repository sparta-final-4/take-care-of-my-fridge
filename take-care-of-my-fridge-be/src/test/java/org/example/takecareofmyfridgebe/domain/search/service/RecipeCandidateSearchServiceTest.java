package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSpicyLevel;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeTasteLevel;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.CandidateHit;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.Condition;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.HitSource;
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
    @DisplayName("100건까지 찾되 문서 본문은 맛과 매운 정도만 받고, 조건 중 하나 이상 맞아야 한다")
    void requestsPoolWithTasteFieldsOnly() {
        // Given
        Condition condition = condition(List.of(1L), null, false, null, null, null);

        // When
        SearchRequest request = service.searchRequest(condition, service.shouldQueries(condition));

        // Then
        assertThat(request.index()).containsExactly("recipes");
        assertThat(request.size()).isEqualTo(100);
        assertThat(request.source().filter().includes()).containsExactly("tastes", "spicy_level");
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
                "category", "keyword", "selected", "imminent", "fridge", "taste");
        assertThat(shoulds.get(1).bool().boost()).isEqualTo(4.0f);
        assertThat(shoulds.get(1).bool().should()).extracting(q -> q.multiMatch().query())
                .containsExactly("김치찌개", "된장찌개");
        assertThat(termsValues(shoulds.get(5).bool().must().get(0))).containsExactly("SPICY");
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
        stubHits(List.of(
                Hit.<HitSource>of(hit -> hit.index("recipes_1").id("10").score(5.0)
                        .matchedQueries(Map.of("selected", 3.0, "fridge", 2.0))
                        .source(new HitSource(List.of("SPICY", "UMAMI"), "HOT"))),
                Hit.<HitSource>of(hit -> hit.index("recipes_1").id("20").score(1.0)
                        .matchedQueries(Map.of("fridge", 1.0))
                        .source(new HitSource(List.of(), "NONE")))));

        // When
        List<CandidateHit> hits = service.search(condition(List.of(1L), List.of(2L), false, null, null, null));

        // Then
        assertThat(hits).extracting(CandidateHit::recipeId).containsExactly(10L, 20L);
        assertThat(hits.get(0).score()).isEqualTo(5.0);
        assertThat(hits.get(0).matched(RecipeCandidateSearchService.MATCH_SELECTED)).isTrue();
        assertThat(hits.get(1).matched(RecipeCandidateSearchService.MATCH_SELECTED)).isFalse();
        assertThat(hits.get(1).matched()).containsExactly("fridge");
        assertThat(hits.get(0).tastes()).containsExactlyInAnyOrder(Member.Taste.SPICY, Member.Taste.UMAMI);
        assertThat(hits.get(0).spicyLevel()).isEqualTo(RecipeSpicyLevel.HOT);
        assertThat(hits.get(1).tastes()).isEmpty();
        assertThat(hits.get(1).spicyLevel()).isEqualTo(RecipeSpicyLevel.NONE);
    }

    @Test
    @DisplayName("ES 문서의 spicy_level 필드를 매운 정도로 읽는다")
    void readsSnakeCaseSpicyLevel() throws IOException {
        // Given
        String json = "{\"tastes\":[\"SPICY\",\"UMAMI\"],\"spicy_level\":\"HOT\"}";

        // When
        HitSource source = new JacksonJsonpMapper().objectMapper().readValue(json, HitSource.class);

        // Then
        assertThat(source).isEqualTo(new HitSource(List.of("SPICY", "UMAMI"), "HOT"));
    }

    @Test
    @DisplayName("문서에 맛과 매운 정도가 없으면 맛은 비우고 매운 정도는 NONE으로 둔다")
    void fillsEmptyTastesAndNone_whenSourceFieldsMissing() throws IOException {
        // Given: 재색인 전 인덱스처럼 필드가 없는 문서와 본문이 없는 문서
        stubHits(List.of(
                Hit.<HitSource>of(hit -> hit.index("recipes_1").id("10").score(1.0)
                        .source(new HitSource(null, null))),
                Hit.<HitSource>of(hit -> hit.index("recipes_1").id("20").score(1.0))));

        // When
        List<CandidateHit> hits = service.search(condition(List.of(1L), null, false, null, null, null));

        // Then
        assertThat(hits).extracting(CandidateHit::tastes).containsExactly(Set.of(), Set.of());
        assertThat(hits).extracting(CandidateHit::spicyLevel).containsExactly(RecipeSpicyLevel.NONE, RecipeSpicyLevel.NONE);
    }

    @Test
    @DisplayName("맛에 매운맛이 있으면 확실히 매운 레시피이고, 매울 수 있는 레시피에도 든다")
    void isDefinitelyAndMaybeSpicy_whenTasteIsSpicy() {
        // Given
        CandidateHit hit = new CandidateHit(1L, 1.0, Set.of(), Set.of(Member.Taste.SPICY), RecipeSpicyLevel.HOT);

        // When, Then
        assertThat(hit.definitelySpicy()).isTrue();
        assertThat(hit.maybeSpicy()).isTrue();
    }

    @Test
    @DisplayName("매운 정도가 MILD면 확실히 매운 레시피는 아니지만 매울 수 있는 레시피다")
    void isOnlyMaybeSpicy_whenMild() {
        // Given
        CandidateHit hit = new CandidateHit(1L, 1.0, Set.of(), Set.of(Member.Taste.SWEET), RecipeSpicyLevel.MILD);

        // When, Then
        assertThat(hit.definitelySpicy()).isFalse();
        assertThat(hit.maybeSpicy()).isTrue();
    }

    @Test
    @DisplayName("맛과 매운 정도 없이 만들면 맛은 비어 있고 매운 정도는 NONE이다")
    void isNotSpicy_whenCreatedWithoutTastes() {
        // Given
        CandidateHit hit = new CandidateHit(1L, 1.0, Set.of("fridge"));

        // When, Then
        assertThat(hit.tastes()).isEmpty();
        assertThat(hit.spicyLevel()).isEqualTo(RecipeSpicyLevel.NONE);
        assertThat(hit.maybeSpicy()).isFalse();
    }

    @Test
    @DisplayName("레시피 id로 맛·매운 정도를 읽을 때 id 조건·맛 필드만 받아 한 번에 검색하고, 없는 id는 결과에서 빠진다")
    void readsTasteLevelsByIds() throws IOException {
        // Given
        stubHits(List.of(
                Hit.<HitSource>of(hit -> hit.index("recipes_1").id("80").score(1.0)
                        .source(new HitSource(List.of("SALTY"), "MILD"))),
                Hit.<HitSource>of(hit -> hit.index("recipes_1").id("81").score(1.0)
                        .source(new HitSource(List.of("SPICY"), "HOT")))));

        // When
        Map<Long, RecipeTasteLevel> levels = service.findTasteLevels(List.of(80L, 81L, 99L));
        SearchRequest request = service.tasteLevelRequest(List.of("80", "81", "99"));

        // Then
        assertThat(levels).containsOnly(
                Map.entry(80L, new RecipeTasteLevel(Set.of(Member.Taste.SALTY), RecipeSpicyLevel.MILD)),
                Map.entry(81L, new RecipeTasteLevel(Set.of(Member.Taste.SPICY), RecipeSpicyLevel.HOT)));
        assertThat(request.query().ids().values()).containsExactly("80", "81", "99");
        assertThat(request.size()).isEqualTo(3);
        assertThat(request.source().filter().includes()).containsExactlyElementsOf(RecipeCandidateSearchService.SOURCE_FIELDS);
    }

    @Test
    @DisplayName("레시피 id가 없으면 ES를 부르지 않고 빈 Map을 돌려준다")
    void returnsEmptyTasteLevels_whenNoIds() throws IOException {
        assertThat(service.findTasteLevels(List.of())).isEmpty();
        verifyNoInteractions(esClient);
    }

    private void stubHits(List<Hit<HitSource>> hits) throws IOException {
        SearchResponse<HitSource> response = SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(hits.size()).relation(TotalHitsRelation.Eq))
                        .hits(hits)));
        when(esClient.search(any(SearchRequest.class), eq(HitSource.class))).thenReturn(response);
    }

    private String queryName(Query query) {
        return query.isMultiMatch() ? query.multiMatch().queryName() : query.bool().queryName();
    }
}

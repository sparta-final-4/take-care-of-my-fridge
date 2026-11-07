package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIndexSource;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeIndexSourceService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSpicyLevel;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeDocumentReaderEmbeddingTest {

    private final RecipeIndexSourceService recipeIndexSourceService = mock(RecipeIndexSourceService.class);
    private final RecipeEmbedder embedder = mock(RecipeEmbedder.class);
    private final ChatCandidateProperties spicyProperties = new ChatCandidateProperties(List.of(), 0.4,
            List.of("청양고추", "고춧가루", "고추장", "김치"), List.of("백김치"), List.of("홍고추"),
            List.of("고추", "붉은고추", "고추냉이"));

    private RecipeDocumentReader reader(boolean embedding) {
        RecipeSearchProperties properties = new RecipeSearchProperties(
                SearchEngine.ELASTICSEARCH,
                new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500, embedding), null, null),
                null);
        return new RecipeDocumentReader(recipeIndexSourceService, embedder, properties, spicyProperties);
    }

    private RecipeIndexSource recipe(long id, String name, String summary, String tags, List<RecipeIndexSource.Ingredient> ingredients) {
        return recipe(id, name, summary, tags, ingredients, Set.of());
    }

    private RecipeIndexSource recipe(long id, String name, String summary, String tags,
                                     List<RecipeIndexSource.Ingredient> ingredients, Set<Member.Taste> tastes) {
        return new RecipeIndexSource(id, "TEST", "SRC_" + id, name, summary, "한식", "찌개", "끓이기", null,
                null, null, 2, null, null, null, null, null, null, tags, null, null, null, ingredients, List.of("끓인다"),
                tastes);
    }

    private static RecipeIndexSource.Ingredient ingredient(String name, String role) {
        return new RecipeIndexSource.Ingredient(null, name, name, role, false);
    }

    private static RecipeIndexSource.Ingredient ingredient(long id, String name, String displayName, boolean basic) {
        return new RecipeIndexSource.Ingredient(id, name, displayName, "부재료", basic);
    }

    @Test
    @DisplayName("임베딩 텍스트는 이름, 요약, 분류, 재료(중복 제거), 태그를 줄로 나눠 넣고 조리 단계는 뺀다")
    void embeddingText_joinsSearchableFields() {
        // Given
        RecipeIndexSource kimchiStew = recipe(1L, "김치찌개", " 칼칼한 국물 ", "매운맛, 찌개", List.of(
                ingredient("김치", "주재료"),
                ingredient("돼지고기", "주재료"),
                ingredient("김치", "부재료")));

        // When
        String text = RecipeDocumentReader.embeddingText(kimchiStew);

        // Then
        assertThat(text).isEqualTo("""
                김치찌개
                칼칼한 국물
                분류: 한식, 찌개, 끓이기
                재료: 김치, 돼지고기
                태그: 매운맛, 찌개""");
    }

    @Test
    @DisplayName("요약, 재료, 태그가 비어 있으면 그 줄을 넣지 않는다")
    void embeddingText_skipsEmptyFields() {
        // Given
        RecipeIndexSource plain = recipe(1L, "흰밥", null, null, List.of());

        // When
        String text = RecipeDocumentReader.embeddingText(plain);

        // Then
        assertThat(text).isEqualTo("흰밥\n분류: 한식, 찌개, 끓이기");
    }

    @Test
    @DisplayName("임베딩을 켜면 readAll은 모든 레시피를 한 번에 넘겨 입력 순서대로 벡터를 붙인다")
    void readAll_attachesEmbeddingsInOrder() {
        // Given
        when(recipeIndexSourceService.findAll()).thenReturn(List.of(
                recipe(1L, "두부조림", null, null, List.of()),
                recipe(2L, "계란말이", null, null, List.of())));
        when(embedder.embedAll(anyList())).thenReturn(List.of(new float[]{1, 0}, new float[]{2, 0}));

        // When
        List<EsDocument> docs = reader(true).readAll();

        // Then
        verify(embedder, times(1)).embedAll(List.of(
                "두부조림\n분류: 한식, 찌개, 끓이기",
                "계란말이\n분류: 한식, 찌개, 끓이기"));
        assertThat(docs.get(0).id()).isEqualTo(1L);
        assertThat((float[]) docs.get(0).source().get("embedding")).containsExactly(new float[]{1, 0});
        assertThat((float[]) docs.get(1).source().get("embedding")).containsExactly(new float[]{2, 0});
    }

    @Test
    @DisplayName("임베딩을 켜면 readOne도 벡터를 붙인다")
    void readOne_attachesEmbedding() {
        // Given
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "두부조림", null, null, List.of())));
        when(embedder.embedAll(anyList())).thenReturn(List.of(new float[]{1, 0}));

        // When
        EsDocument doc = reader(true).readOne(1L).orElseThrow();

        // Then
        assertThat((float[]) doc.source().get("embedding")).containsExactly(new float[]{1, 0});
    }

    @Test
    @DisplayName("임베딩이 실패하면 예외 없이 embedding 필드만 빼고 색인한다")
    void readAll_indexesWithoutEmbedding_whenEmbedderFails() {
        // Given
        when(recipeIndexSourceService.findAll()).thenReturn(List.of(recipe(1L, "두부조림", null, null, List.of())));
        when(embedder.embedAll(anyList())).thenThrow(new IllegalStateException("OpenAI 장애"));

        // When
        List<EsDocument> docs = reader(true).readAll();

        // Then
        assertThat(docs.size()).isEqualTo(1);
        assertThat(docs.get(0).source().get("name")).isEqualTo("두부조림");
        assertThat(docs.get(0).source().containsKey("embedding")).isFalse();
    }

    @Test
    @DisplayName("임베딩을 끄면 임베딩을 호출하지 않고 embedding 필드도 넣지 않는다")
    void readAll_skipsEmbedding_whenDisabled() {
        // Given
        when(recipeIndexSourceService.findAll()).thenReturn(List.of(recipe(1L, "두부조림", null, null, List.of())));

        // When
        List<EsDocument> docs = reader(false).readAll();

        // Then
        assertThat(docs.get(0).source().containsKey("embedding")).isFalse();
        verify(embedder, never()).embedAll(anyList());
    }

    @Test
    @DisplayName("재료 id는 중복 없이 넣고, 기본 양념을 뺀 재료 종류 수를 따로 넣는다")
    void putsIngredientIdsAndNonBasicCount() {
        // Given
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "두부조림", null, null, List.of(
                ingredient(10L, "두부", "두부", false),
                ingredient(20L, "간장", "진간장", true),
                ingredient(10L, "두부", "부침용 두부", false),
                ingredient(30L, "대파", "대파", false)))));

        // When
        Map<String, Object> doc = reader(false).readOne(1L).orElseThrow().source();

        // Then
        assertThat(doc.get("ingredient_ids")).isEqualTo(List.of(10L, 20L, 30L));
        assertThat(doc.get("non_basic_ingredient_count")).isEqualTo(2);
    }

    @Test
    @DisplayName("확실히 매운 재료가 있으면 HOT이고 맛에 매운맛을 더한다")
    void putsHotAndSpicyTaste_whenDefinitelySpicyIngredient() {
        // Given: 사전 이름은 "고추"로 합쳐졌어도 레시피에 적힌 이름(다진 청양고추)으로 잡는다
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "제육볶음", null, null, List.of(
                ingredient(10L, "고추", "다진 청양고추", false)), Set.of(Member.Taste.SWEET))));

        // When
        Map<String, Object> doc = reader(false).readOne(1L).orElseThrow().source();

        // Then
        assertThat(doc.get("spicy_level")).isEqualTo(RecipeSpicyLevel.HOT.name());
        assertThat(doc.get("tastes")).isEqualTo(List.of("SWEET", "SPICY"));
    }

    @Test
    @DisplayName("매울 수 있는 재료만 있으면 MILD이고 맛에 매운맛을 더하지 않는다")
    void putsMildWithoutSpicyTaste_whenOnlyMildIngredient() {
        // Given
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "잡채", null, null, List.of(
                ingredient(10L, "고추", "홍고추", false)))));

        // When
        Map<String, Object> doc = reader(false).readOne(1L).orElseThrow().source();

        // Then
        assertThat(doc.get("spicy_level")).isEqualTo(RecipeSpicyLevel.MILD.name());
        assertThat(doc.get("tastes")).isEqualTo(List.of());
    }

    @ParameterizedTest(name = "사전 이름 {0} / 레시피에 적힌 이름 {1} → {2}")
    @CsvSource({
            "고추, 고추, MILD",
            "고추, 붉은 고추, MILD",
            "와사비, 고추냉이, MILD",
            "오이고추, 아삭이고추, NONE",
            "오이고추, 오이고추, NONE",
            "꽈리고추, 꽈리고추, NONE",
            "풋고추, 풋고추, NONE",
            "고추장, 고추장, HOT",
            "고추, 청양고추, HOT"
    })
    @DisplayName("고추·붉은고추·고추냉이는 이름이 똑같을 때만 MILD이고, 오이고추·꽈리고추·풋고추는 MILD가 아니며, 고추장·청양고추는 HOT이다")
    void putsSpicyLevel_byExactPepperName(String name, String displayName, RecipeSpicyLevel expected) {
        // Given
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "볶음", null, null, List.of(
                ingredient(10L, name, displayName, false)))));

        // When
        Map<String, Object> doc = reader(false).readOne(1L).orElseThrow().source();

        // Then
        assertThat(doc.get("spicy_level")).isEqualTo(expected.name());
    }

    @Test
    @DisplayName("제외 목록 김치(백김치)만 있으면 NONE이다")
    void putsNone_whenOnlyExcludedKimchi() {
        // Given
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "백김치말이", null, null, List.of(
                ingredient(10L, "백김치", "백김치", false)))));

        // When
        Map<String, Object> doc = reader(false).readOne(1L).orElseThrow().source();

        // Then
        assertThat(doc.get("spicy_level")).isEqualTo(RecipeSpicyLevel.NONE.name());
    }

    @Test
    @DisplayName("매운 재료가 없어도 레시피 맛 데이터가 매운맛이면 HOT이다")
    void putsHot_whenTasteIsSpicyWithoutSpicyIngredient() {
        // Given: 겨자는 매운 재료 목록에 없다
        when(recipeIndexSourceService.findOne(1L)).thenReturn(Optional.of(recipe(1L, "새우겨자채", null, null, List.of(
                ingredient(10L, "새우", "새우", false),
                ingredient(20L, "겨자", "연겨자", false)), Set.of(Member.Taste.SPICY))));

        // When
        Map<String, Object> doc = reader(false).readOne(1L).orElseThrow().source();

        // Then
        assertThat(doc.get("spicy_level")).isEqualTo(RecipeSpicyLevel.HOT.name());
        assertThat(doc.get("tastes")).isEqualTo(List.of("SPICY"));
    }

}

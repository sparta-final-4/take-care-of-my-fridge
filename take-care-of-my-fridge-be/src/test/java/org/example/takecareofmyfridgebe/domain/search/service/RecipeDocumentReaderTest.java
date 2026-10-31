package org.example.takecareofmyfridgebe.domain.search.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeIndexSourceService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RecipeIndexSourceService.class, RecipeDocumentReader.class,
        RecipeDocumentReaderTest.EmbeddingDisabled.class})
class RecipeDocumentReaderTest extends MySqlContainerSupport {

    // 임베딩은 RecipeDocumentReaderEmbeddingTest에서 본다. 여기서는 DB에서 읽은 필드만 확인한다
    @TestConfiguration
    static class EmbeddingDisabled {
        @Bean
        RecipeSearchProperties recipeSearchProperties() {
            return new RecipeSearchProperties(
                    SearchEngine.ELASTICSEARCH,
                    new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500, false), null, null),
                    null);
        }
    }

    @MockitoBean
    private RecipeEmbedder recipeEmbedder;

    @Autowired
    private RecipeDocumentReader recipeDocumentReader;

    @Autowired
    private EntityManager em;

    private Ingredient createIngredient(String name) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode("ING_" + System.nanoTime())
                .name(name)
                .baseUnit("g")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .build();
        em.persist(ingredient);
        return ingredient;
    }

    private Recipe createRecipe(String name, String tags) {
        Recipe recipe = Recipe.builder()
                .source("TEST")
                .sourceId("SRC_" + System.nanoTime())
                .name(name)
                .cuisineType("한식")
                .category("찌개")
                .difficulty(2)
                .tags(tags)
                .build();
        em.persist(recipe);
        return recipe;
    }

    private void addIngredient(Recipe recipe, Ingredient ingredient, int seq, String role) {
        em.persist(RecipeIngredient.builder()
                .recipe(recipe)
                .ingredientId(ingredient.getId())
                .seq(seq)
                .displayName(ingredient.getName())
                .role(role)
                .build());
    }

    private void addStep(Recipe recipe, int stepNo, String description) {
        em.persist(RecipeStep.builder()
                .recipe(recipe)
                .stepNo(stepNo)
                .description(description)
                .build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("readOne은 재료(seq 순, 중복 제거), 주재료, 태그, 조리 단계를 ES 문서 필드로 만든다")
    void readOneBuildsDocument() {
        // Given: 재료는 seq를 뒤섞어 저장하고, 같은 재료를 두 번 넣는다
        Ingredient kimchi = createIngredient("김치_" + System.nanoTime());
        Ingredient pork = createIngredient("돼지고기_" + System.nanoTime());
        Ingredient onion = createIngredient("양파_" + System.nanoTime());
        Recipe recipe = createRecipe("김치찌개", " 찌개, 매운맛 ,찌개,, ");
        addIngredient(recipe, onion, 3, "부재료");
        addIngredient(recipe, kimchi, 1, "주재료");
        addIngredient(recipe, pork, 2, "주재료");
        addIngredient(recipe, kimchi, 4, "부재료");
        addStep(recipe, 2, "김치를 넣고 끓인다");
        addStep(recipe, 1, "고기를 볶는다");
        flushAndClear();

        // When
        EsDocument esDoc = recipeDocumentReader.readOne(recipe.getId()).orElseThrow();
        Map<String, Object> doc = esDoc.source();

        // Then
        assertEquals(recipe.getId(), esDoc.id());
        assertEquals(recipe.getId(), doc.get("id"));
        assertEquals("김치찌개", doc.get("name"));
        assertEquals("TEST", doc.get("source"));
        assertEquals(List.of("찌개", "매운맛"), doc.get("tags"));
        assertEquals(List.of(kimchi.getName(), pork.getName(), onion.getName()), doc.get("ingredients"));
        assertEquals(List.of(kimchi.getName(), pork.getName()), doc.get("main_ingredients"));
        assertEquals(3, doc.get("ingredient_count"));
        assertEquals("고기를 볶는다\n김치를 넣고 끓인다", doc.get("steps_text"));
        assertEquals(2, doc.get("step_count"));
        assertTrue(((String) doc.get("created_at")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("재료와 조리 단계가 없고 태그가 null이면 빈 목록과 0으로 채운다")
    void readOneWithoutChildren() {
        // Given
        Recipe recipe = createRecipe("빈 레시피", null);
        flushAndClear();

        // When
        Map<String, Object> doc = recipeDocumentReader.readOne(recipe.getId()).orElseThrow().source();

        // Then
        assertEquals(List.of(), doc.get("tags"));
        assertEquals(List.of(), doc.get("ingredients"));
        assertEquals(List.of(), doc.get("main_ingredients"));
        assertEquals(0, doc.get("ingredient_count"));
        assertEquals("", doc.get("steps_text"));
        assertEquals(0, doc.get("step_count"));
    }

    @Test
    @DisplayName("없는 레시피는 readOne이 빈 Optional을 반환한다")
    void readOneReturnsEmptyWhenMissing() {
        // When
        Optional<EsDocument> doc = recipeDocumentReader.readOne(Long.MAX_VALUE);

        // Then
        assertTrue(doc.isEmpty());
    }

    @Test
    @DisplayName("readAll은 id 순으로 모든 레시피를 읽고, 재료와 조리 단계를 레시피별로 나눈다")
    void readAllGroupsByRecipe() {
        // Given
        Ingredient tofu = createIngredient("두부_" + System.nanoTime());
        Ingredient egg = createIngredient("달걀_" + System.nanoTime());
        Recipe first = createRecipe("두부조림", null);
        Recipe second = createRecipe("계란말이", null);
        addIngredient(first, tofu, 1, "주재료");
        addIngredient(second, egg, 1, "주재료");
        addStep(first, 1, "두부를 굽는다");
        addStep(second, 1, "달걀을 푼다");
        addStep(second, 2, "말아 굽는다");
        flushAndClear();

        // When: 다른 테스트 데이터가 섞일 수 있어 이번에 만든 레시피만 고른다
        List<EsDocument> docs = recipeDocumentReader.readAll().stream()
                .filter(doc -> doc.id() == first.getId() || doc.id() == second.getId())
                .toList();

        // Then
        assertEquals(2, docs.size());
        assertEquals(first.getId(), docs.get(0).id());
        assertEquals(List.of(tofu.getName()), docs.get(0).source().get("ingredients"));
        assertEquals(1, docs.get(0).source().get("step_count"));
        assertEquals(second.getId(), docs.get(1).id());
        assertEquals(List.of(egg.getName()), docs.get(1).source().get("ingredients"));
        assertEquals("달걀을 푼다\n말아 굽는다", docs.get(1).source().get("steps_text"));
    }
}

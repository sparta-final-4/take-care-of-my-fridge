package org.example.takecareofmyfridgebe.domain.search.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeTaste;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RecipeIndexSourceService.class, RecipeDocumentReader.class,
        RecipeDocumentReaderTest.EmbeddingDisabled.class})
class RecipeDocumentReaderTest extends MySqlContainerSupport {

    // 임베딩은 RecipeDocumentReaderEmbeddingTest에서 본다. 여기서는 DB에서 읽은 필드만 확인한다
    @TestConfiguration
    static class EmbeddingDisabled {
        @Bean
        ChatCandidateProperties chatCandidateProperties() {
            return new ChatCandidateProperties(List.of(), 0.4, List.of("청양고추"), List.of(), List.of("홍고추"));
        }

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
    void readOne_buildsDocument() {
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
        assertThat(esDoc.id()).isEqualTo(recipe.getId());
        assertThat(doc.get("id")).isEqualTo(recipe.getId());
        assertThat(doc.get("name")).isEqualTo("김치찌개");
        assertThat(doc.get("source")).isEqualTo("TEST");
        assertThat(doc.get("tags")).isEqualTo(List.of("찌개", "매운맛"));
        assertThat(doc.get("ingredients")).isEqualTo(List.of(kimchi.getName(), pork.getName(), onion.getName()));
        assertThat(doc.get("main_ingredients")).isEqualTo(List.of(kimchi.getName(), pork.getName()));
        assertThat(doc.get("ingredient_count")).isEqualTo(3);
        assertThat(doc.get("steps_text")).isEqualTo("고기를 볶는다\n김치를 넣고 끓인다");
        assertThat(doc.get("step_count")).isEqualTo(2);
        assertThat(((String) doc.get("created_at")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")).isTrue();
    }

    @Test
    @DisplayName("재료와 조리 단계가 없고 태그가 null이면 빈 목록과 0으로 채운다")
    void readOne_fillsEmptyValues_whenNoChildren() {
        // Given
        Recipe recipe = createRecipe("빈 레시피", null);
        flushAndClear();

        // When
        Map<String, Object> doc = recipeDocumentReader.readOne(recipe.getId()).orElseThrow().source();

        // Then
        assertThat(doc.get("tags")).isEqualTo(List.of());
        assertThat(doc.get("ingredients")).isEqualTo(List.of());
        assertThat(doc.get("main_ingredients")).isEqualTo(List.of());
        assertThat(doc.get("ingredient_count")).isEqualTo(0);
        assertThat(doc.get("steps_text")).isEqualTo("");
        assertThat(doc.get("step_count")).isEqualTo(0);
    }

    @Test
    @DisplayName("없는 레시피는 readOne이 빈 Optional을 반환한다")
    void readOne_returnsEmpty_whenRecipeMissing() {
        // When
        Optional<EsDocument> doc = recipeDocumentReader.readOne(Long.MAX_VALUE);

        // Then
        assertThat(doc.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("readAll은 id 순으로 모든 레시피를 읽고, 재료와 조리 단계를 레시피별로 나눈다")
    void readAll_groupsByRecipe() {
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
        assertThat(docs.size()).isEqualTo(2);
        assertThat(docs.get(0).id()).isEqualTo(first.getId());
        assertThat(docs.get(0).source().get("ingredients")).isEqualTo(List.of(tofu.getName()));
        assertThat(docs.get(0).source().get("step_count")).isEqualTo(1);
        assertThat(docs.get(1).id()).isEqualTo(second.getId());
        assertThat(docs.get(1).source().get("ingredients")).isEqualTo(List.of(egg.getName()));
        assertThat(docs.get(1).source().get("steps_text")).isEqualTo("달걀을 푼다\n말아 굽는다");
    }

    @Test
    @DisplayName("readOne은 재료 id, 기본 양념 뺀 재료 수, 레시피 맛, 레시피에 적힌 이름으로 가른 매운 정도를 넣는다")
    void readOne_putsCandidateSearchFields() {
        // Given: 사전 이름은 "고추"지만 레시피에는 "청양고추"로 적혀 있다
        Ingredient pork = createIngredient("돼지고기_" + System.nanoTime());
        Ingredient pepper = createIngredient("고추_" + System.nanoTime());
        Ingredient salt = Ingredient.builder()
                .ingredientCode("ING_" + System.nanoTime())
                .name("소금_" + System.nanoTime())
                .baseUnit("g")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(365)
                .basicSeasoning(true)
                .build();
        em.persist(salt);
        Recipe recipe = createRecipe("제육볶음", null);
        addIngredient(recipe, pork, 1, "주재료");
        em.persist(RecipeIngredient.builder()
                .recipe(recipe)
                .ingredientId(pepper.getId())
                .seq(2)
                .displayName("청양고추")
                .role("부재료")
                .build());
        addIngredient(recipe, salt, 3, "양념");
        em.persist(RecipeTaste.builder().recipe(recipe).taste(Member.Taste.SWEET).build());
        flushAndClear();

        // When
        Map<String, Object> doc = recipeDocumentReader.readOne(recipe.getId()).orElseThrow().source();

        // Then
        assertThat(doc.get("ingredient_ids")).isEqualTo(List.of(pork.getId(), pepper.getId(), salt.getId()));
        assertThat(doc.get("non_basic_ingredient_count")).isEqualTo(2);
        assertThat(doc.get("spicy_level")).isEqualTo("HOT");
        assertThat(doc.get("tastes")).isEqualTo(List.of("SWEET", "SPICY"));
    }
}

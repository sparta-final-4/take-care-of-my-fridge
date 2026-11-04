package org.example.takecareofmyfridgebe.domain.recipe.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateInfo;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeTaste;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RecipeService.class})
class RecipeCandidateQueryTest extends MySqlContainerSupport {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private IngredientService ingredientService;

    private Ingredient egg;
    private Ingredient tofu;
    private Ingredient salt;
    private Recipe eggTofuSoup;
    private Recipe saltOnly;
    private Recipe noTasteRecipe;

    @BeforeEach
    void setUp() {
        egg = ingredient("ING_EGG", "계란", false);
        tofu = ingredient("ING_TOFU", "두부", false);
        salt = ingredient("ING_SALT", "소금", true);

        eggTofuSoup = recipe("R1", "계란두부국", "국", 20);
        // 같은 재료가 양념 그룹 등으로 두 번 나와도 한 줄로 센다
        recipeIngredient(eggTofuSoup, 1, egg);
        recipeIngredient(eggTofuSoup, 2, tofu);
        recipeIngredient(eggTofuSoup, 3, salt);
        recipeIngredient(eggTofuSoup, 4, egg);

        saltOnly = recipe("R2", "소금물", "기타", null);
        recipeIngredient(saltOnly, 1, salt);

        noTasteRecipe = recipe("R3", "두부부침", "전", 15);
        recipeIngredient(noTasteRecipe, 1, tofu);

        entityManager.persist(RecipeTaste.builder().recipe(eggTofuSoup).taste(Taste.MILD).build());
        entityManager.persist(RecipeTaste.builder().recipe(eggTofuSoup).taste(Taste.UMAMI).build());
        entityManager.persist(RecipeTaste.builder().recipe(saltOnly).taste(Taste.SALTY).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("RecipeTaste 엔티티로 dev와 같은 이름의 recipe_tastes 테이블·유니크·인덱스가 생긴다")
    void createsRecipeTastesTable_fromEntity() {
        @SuppressWarnings("unchecked")
        List<Object[]> indexes = entityManager.createNativeQuery("""
                SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recipe_tastes'
                GROUP BY INDEX_NAME, NON_UNIQUE
                """).getResultList();
        Object columnType = entityManager.createNativeQuery("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recipe_tastes' AND COLUMN_NAME = 'taste'
                """).getSingleResult();

        assertThat(indexes)
                .extracting(row -> row[0].toString(), row -> ((Number) row[1]).intValue(), row -> row[2].toString())
                .contains(
                        tuple("uk_recipe_tastes_recipe_id_taste", 0, "recipe_id,taste"),
                        tuple("idx_recipe_tastes_taste_recipe_id", 1, "taste,recipe_id"));
        // Hibernate는 enum 값을 알파벳 순으로 선언한다(dev는 SALTY,SWEET,… 순). 값 집합만 같으면 매핑은 같다
        assertThat(columnType.toString()).startsWith("enum(");
        assertThat(columnType.toString().replaceAll("enum\\(|\\)|'", "").split(","))
                .containsExactlyInAnyOrder("SALTY", "SWEET", "SPICY", "UMAMI", "MILD");
    }

    @Test
    @DisplayName("레시피별 맛을 묶어 돌려주고 맛이 없는 레시피는 빠진다")
    void returnsTastesByRecipe() {
        Map<Long, Set<Taste>> tastes = recipeService.findTastesByRecipeIds(
                List.of(eggTofuSoup.getId(), saltOnly.getId(), noTasteRecipe.getId()));

        assertThat(tastes).containsOnlyKeys(eggTofuSoup.getId(), saltOnly.getId());
        assertThat(tastes.get(eggTofuSoup.getId())).containsExactlyInAnyOrder(Taste.MILD, Taste.UMAMI);
        assertThat(tastes.get(saltOnly.getId())).containsExactly(Taste.SALTY);
    }

    @Test
    @DisplayName("재료가 들어간 레시피 재료를 레시피·재료 쌍마다 한 줄로 기본 양념 여부와 함께 돌려준다")
    void returnsDistinctCandidateIngredients() {
        List<RecipeCandidateIngredient> rows = recipeService.findCandidateIngredients(List.of(egg.getId(), salt.getId()));

        assertThat(rows)
                .extracting(RecipeCandidateIngredient::recipeId, RecipeCandidateIngredient::ingredientId,
                        RecipeCandidateIngredient::ingredientName, RecipeCandidateIngredient::basicSeasoning)
                .containsExactlyInAnyOrder(
                        tuple(eggTofuSoup.getId(), egg.getId(), "계란", false),
                        tuple(eggTofuSoup.getId(), salt.getId(), "소금", true),
                        tuple(saltOnly.getId(), salt.getId(), "소금", true));
    }

    @Test
    @DisplayName("레시피 정보에 기본 양념을 뺀 재료 종류 수를 담고, 재료가 모두 기본 양념이면 0이다")
    void returnsInfosWithNonBasicIngredientCount() {
        Map<Long, RecipeCandidateInfo> infos = recipeService.findCandidateInfos(
                List.of(eggTofuSoup.getId(), saltOnly.getId()));

        assertThat(infos.get(eggTofuSoup.getId()))
                .isEqualTo(new RecipeCandidateInfo(eggTofuSoup.getId(), "계란두부국", "국", 20, 2));
        assertThat(infos.get(saltOnly.getId()))
                .isEqualTo(new RecipeCandidateInfo(saltOnly.getId(), "소금물", "기타", null, 0));
    }

    @Test
    @DisplayName("레시피별 기본 양념을 뺀 재료를 재료마다 한 줄로 레시피에 처음 나온 순서대로 돌려준다")
    void returnsNonBasicIngredients_inRecipeOrder() {
        List<RecipeCandidateIngredient> rows = recipeService.findNonBasicIngredients(
                List.of(eggTofuSoup.getId(), saltOnly.getId(), noTasteRecipe.getId()));

        assertThat(rows)
                .extracting(RecipeCandidateIngredient::recipeId, RecipeCandidateIngredient::ingredientId,
                        RecipeCandidateIngredient::ingredientName, RecipeCandidateIngredient::basicSeasoning)
                .containsExactly(
                        tuple(eggTofuSoup.getId(), egg.getId(), "계란", false),
                        tuple(eggTofuSoup.getId(), tofu.getId(), "두부", false),
                        tuple(noTasteRecipe.getId(), tofu.getId(), "두부", false));
    }

    @Test
    @DisplayName("빈 ID 목록이면 쿼리 없이 빈 결과를 돌려준다")
    void returnsEmpty_whenIdsAreEmpty() {
        assertThat(recipeService.findNonBasicIngredients(List.of())).isEmpty();
        assertThat(recipeService.findCandidateIngredients(List.of())).isEmpty();
        assertThat(recipeService.findCandidateInfos(List.of())).isEmpty();
        assertThat(recipeService.findTastesByRecipeIds(List.of())).isEmpty();
    }

    private Ingredient ingredient(String code, String name, boolean basicSeasoning) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(basicSeasoning)
                .build();
        entityManager.persist(ingredient);
        return ingredient;
    }

    private Recipe recipe(String sourceId, String name, String category, Integer cookTimeMin) {
        Recipe recipe = Recipe.builder()
                .source("MFDS")
                .sourceId(sourceId)
                .name(name)
                .category(category)
                .cookTimeMin(cookTimeMin)
                .difficulty(1)
                .build();
        entityManager.persist(recipe);
        return recipe;
    }

    private void recipeIngredient(Recipe recipe, int seq, Ingredient ingredient) {
        entityManager.persist(RecipeIngredient.builder()
                .recipe(recipe)
                .ingredientId(ingredient.getId())
                .seq(seq)
                .displayName(ingredient.getName())
                .build());
    }
}

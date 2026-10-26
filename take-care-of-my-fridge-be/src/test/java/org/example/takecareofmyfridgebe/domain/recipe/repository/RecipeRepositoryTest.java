package org.example.takecareofmyfridgebe.domain.recipe.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdatedAtDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class RecipeRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private EntityManager em;

    private Recipe createTestRecipe(String name, String tipSummary) {
        Recipe recipe = Recipe.builder()
                .source("TEST")
                .sourceId("SRC_" + System.nanoTime())
                .name(name)
                .cuisineType("한식")
                .category("찌개")
                .difficulty(3)
                .tipSummary(tipSummary)
                .build();
        em.persist(recipe);
        em.flush();
        return recipe;
    }

    @Test
    @DisplayName("같은 요약을 다시 저장해도 @Modifying UPDATE 쿼리에 의해 updated_at이 새로운 시각으로 갱신된다")
    void updateTipSummaryUpdatesTimestampEvenWhenSummaryIsIdentical() {
        // Given: 기존에 "동일한 요약"을 가지고 있는 레시피 저장
        String initialSummary = "마늘과 대파를 듬뿍 넣으면 맛있어요.";
        Recipe recipe = createTestRecipe("된장찌개", initialSummary);

        // 과거 시각(5일 전)으로 updated_at 강제 세팅하여 JPA Auditing 시각과 차이를 둠
        LocalDateTime pastTime = LocalDateTime.of(2026, 10, 20, 10, 0, 0);
        em.createNativeQuery("UPDATE recipes SET updated_at = :pastTime WHERE id = :id")
                .setParameter("pastTime", pastTime)
                .setParameter("id", recipe.getId())
                .executeUpdate();

        em.flush();
        em.clear();

        // When: 이전과 동일한 요약 내용으로 updateTipSummary 실행 (현재 시각 전달)
        LocalDateTime updateTime = LocalDateTime.of(2026, 10, 26, 12, 0, 0);
        int affectedRows = recipeRepository.updateTipSummary(recipe.getId(), initialSummary, updateTime);

        // Then: 1개의 행이 영향받음
        assertEquals(1, affectedRows);

        // DB에서 다시 조회하여 검증
        em.clear();
        Recipe refreshed = em.find(Recipe.class, recipe.getId());
        assertNotNull(refreshed);
        assertEquals(initialSummary, refreshed.getTipSummary());
        // JPA Dirty Checking 무시하고 직접 UPDATE에 의해 updated_at이 updateTime으로 갱신됨
        assertEquals(updateTime, refreshed.getUpdatedAt());
    }

    @Test
    @DisplayName("findUpdatedAtsByIdIn으로 여러 레시피의 ID와 최근 수정 시각(updated_at)을 단일 쿼리로 프로젝션 조회한다")
    void findUpdatedAtsByIdInReturnsProjections() {
        // Given
        Recipe recipe1 = createTestRecipe("김치찌개", "김치 팁");
        Recipe recipe2 = createTestRecipe("순두부찌개", null);

        em.flush();
        em.clear();

        // When
        List<RecipeUpdatedAtDto> result = recipeRepository.findUpdatedAtsByIdIn(
                List.of(recipe1.getId(), recipe2.getId())
        );

        // Then
        assertEquals(2, result.size());
        RecipeUpdatedAtDto dto1 = result.stream()
                .filter(r -> r.id().equals(recipe1.getId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(dto1.updatedAt());

        RecipeUpdatedAtDto dto2 = result.stream()
                .filter(r -> r.id().equals(recipe2.getId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(dto2.updatedAt());
    }
}

package org.example.takecareofmyfridgebe.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.math.BigDecimal;

@Entity
@Table(
    name = "recipes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipes_source_source_id", columnNames = {"source", "source_id"})
    },
    indexes = {
        @Index(name = "idx_recipes_name", columnList = "name"),
        @Index(name = "idx_recipes_category", columnList = "category"),
        @Index(name = "idx_recipes_difficulty", columnList = "difficulty")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 출처 (MAFRA: 농식품 레시피 / MFDS: 식약처 COOKRCP01)
    @Column(name = "source", length = 20, nullable = false)
    private String source;

    // 원본 레시피 코드
    @Column(name = "source_id", length = 20, nullable = false)
    private String sourceId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // 음식 종류 (한식/서양/중국…)
    @Column(name = "cuisine_type", length = 20)
    private String cuisineType;

    // 요리 분류 (밥/국/반찬…)
    @Column(name = "category", length = 50)
    private String category;

    // 조리 방법 (끓이기/굽기…)
    @Column(name = "cook_method", length = 20)
    private String cookMethod;

    @Column(name = "main_ingredient_category", length = 30)
    private String mainIngredientCategory;

    @Column(name = "cook_time_min")
    private Integer cookTimeMin;

    // 분량(인분)
    @Column(name = "servings")
    private Integer servings;

    // 난이도 1(쉬움)~10(어려움)
    @Column(name = "difficulty", columnDefinition = "TINYINT", nullable = false)
    private int difficulty;

    // 1인분 중량(g)
    @Column(name = "weight_g", precision = 8, scale = 2)
    private BigDecimal weightG;

    // 열량(kcal)
    @Column(name = "calories", precision = 8, scale = 2)
    private BigDecimal calories;

    @Column(name = "carbs_g", precision = 8, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "protein_g", precision = 8, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 8, scale = 2)
    private BigDecimal fatG;

    @Column(name = "sodium_mg", precision = 8, scale = 2)
    private BigDecimal sodiumMg;

    // 해시태그
    @Column(name = "tags", length = 255)
    private String tags;

    // 저감 조리법 팁
    @Column(name = "tip", columnDefinition = "TEXT")
    private String tip;

    // 후기 요약
    @Column(name = "tip_summary", columnDefinition = "TEXT")
    private String tipSummary;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 재료 원문 (파싱 전)
    @Column(name = "ingredients_raw", columnDefinition = "TEXT")
    private String ingredientsRaw;

    @Builder
    private Recipe(
        String source,
        String sourceId,
        String name,
        String summary,
        String cuisineType,
        String category,
        String cookMethod,
        String mainIngredientCategory,
        Integer cookTimeMin,
        Integer servings,
        int difficulty,
        BigDecimal weightG,
        BigDecimal calories,
        BigDecimal carbsG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sodiumMg,
        String tags,
        String tip,
        String tipSummary,
        String imageUrl,
        String ingredientsRaw
    ) {
        this.source = source;
        this.sourceId = sourceId;
        this.name = name;
        this.summary = summary;
        this.cuisineType = cuisineType;
        this.category = category;
        this.cookMethod = cookMethod;
        this.mainIngredientCategory = mainIngredientCategory;
        this.cookTimeMin = cookTimeMin;
        this.servings = servings;
        this.difficulty = difficulty;
        this.weightG = weightG;
        this.calories = calories;
        this.carbsG = carbsG;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.sodiumMg = sodiumMg;
        this.tags = tags;
        this.tip = tip;
        this.tipSummary = tipSummary;
        this.imageUrl = imageUrl;
        this.ingredientsRaw = ingredientsRaw;
    }

    // 수정은 의미 단위로 나눠 바꾼다. 출처(source, sourceId)는 바꾸지 않고, 후기 요약은 RecipeService.updateTipSummary로만 바꾼다.
    public void changeBasicInfo(String name, String summary, String imageUrl, String tags, String tip) {
        this.name = name;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.tags = tags;
        this.tip = tip;
    }

    public void changeClassification(String cuisineType, String category, String cookMethod,
                                     String mainIngredientCategory) {
        this.cuisineType = cuisineType;
        this.category = category;
        this.cookMethod = cookMethod;
        this.mainIngredientCategory = mainIngredientCategory;
    }

    public void changeCookingInfo(Integer cookTimeMin, Integer servings, int difficulty) {
        this.cookTimeMin = cookTimeMin;
        this.servings = servings;
        this.difficulty = difficulty;
    }

    public void changeNutrition(BigDecimal weightG, BigDecimal calories, BigDecimal carbsG,
                                BigDecimal proteinG, BigDecimal fatG, BigDecimal sodiumMg) {
        this.weightG = weightG;
        this.calories = calories;
        this.carbsG = carbsG;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.sodiumMg = sodiumMg;
    }
}

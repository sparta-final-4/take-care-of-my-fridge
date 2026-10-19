package org.example.takecareofmyfridgebe.domain.ingredient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.time.LocalDate;

@Entity
@Table(
    name = "ingredients",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ingredients_ingredient_code", columnNames = "ingredient_code"),
        @UniqueConstraint(name = "uk_ingredients_name", columnNames = "name")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient extends BaseTimeEntity {

    // 직접 입력 재료 및 밀키트 전용 코드 상수
    public static final String ING_CUSTOM = "ING_CUSTOM";
    public static final String ING_MEALKIT = "ING_MEALKIT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "ingredient_code", length = 50, nullable = false)
    private String ingredientCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "base_unit", length = 20, nullable = false)
    private String baseUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_storage", nullable = false)
    private StorageType defaultStorage;

    @Column(name = "default_shelf_days", nullable = false)
    private int defaultShelfDays;

    @Column(name = "is_basic_seasoning", nullable = false)
    private boolean basicSeasoning;

    @Builder
    private Ingredient(
        String ingredientCode,
        String name,
        String baseUnit,
        StorageType defaultStorage,
        int defaultShelfDays,
        boolean basicSeasoning
    ) {
        this.ingredientCode = ingredientCode;
        this.name = name;
        this.baseUnit = baseUnit;
        this.defaultStorage = defaultStorage;
        this.defaultShelfDays = defaultShelfDays;
        this.basicSeasoning = basicSeasoning;
    }

    // 등록일 기준 소비기한 자동 계산 (등록일 + 기본 소비기한 일수)
    public LocalDate calculateExpirationDate(LocalDate registeredDate) {
        return registeredDate.plusDays(this.defaultShelfDays);
    }

    // 사용자 직접 입력 재료(ING_CUSTOM) 여부 확인
    public boolean isCustom() {
        return ING_CUSTOM.equals(this.ingredientCode);
    }

    // 밀키트(ING_MEALKIT) 여부 확인 (요리 완료 차감 대상 아님)
    public boolean isMealkit() {
        return ING_MEALKIT.equals(this.ingredientCode);
    }
}

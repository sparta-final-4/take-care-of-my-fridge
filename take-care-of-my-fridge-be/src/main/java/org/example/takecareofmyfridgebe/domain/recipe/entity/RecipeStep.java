package org.example.takecareofmyfridgebe.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// DDL에 created_at/updated_at이 없어 BaseTimeEntity를 상속하지 않음
@Entity
@Table(
    name = "recipe_steps",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipe_steps_recipe_id_step_no", columnNames = {"recipe_id", "step_no"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    // 조리 순서 (1부터)
    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 단계별 팁
    @Column(name = "tip", columnDefinition = "TEXT")
    private String tip;

    @Builder
    private RecipeStep(Recipe recipe, int stepNo, String description, String imageUrl, String tip) {
        this.recipe = recipe;
        this.stepNo = stepNo;
        this.description = description;
        this.imageUrl = imageUrl;
        this.tip = tip;
    }
}

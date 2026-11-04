package org.example.takecareofmyfridgebe.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 레시피 맛 (N:M). AI 상담 추천에서 회원 맛 선호와 비교한다.
 * dev DB에 이미 있는 recipe_tastes 테이블(유니크·인덱스·FK 이름 포함)에 맞춘다.
 * 맛 값은 회원 맛 선호와 같은 enum(Member.Taste)을 쓴다.
 */
@Entity
@Table(
    name = "recipe_tastes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipe_tastes_recipe_id_taste", columnNames = {"recipe_id", "taste"})
    },
    indexes = {
        @Index(name = "idx_recipe_tastes_taste_recipe_id", columnList = "taste, recipe_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeTaste extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recipe_tastes_recipe"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    @Enumerated(EnumType.STRING)
    @Column(name = "taste", nullable = false, length = 20)
    private Member.Taste taste;

    @Builder
    private RecipeTaste(Recipe recipe, Member.Taste taste) {
        this.recipe = recipe;
        this.taste = taste;
    }
}

package org.example.takecareofmyfridgebe.domain.review.entity;

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
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(
    name = "recipe_reviews",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_recipe_reviews_member_id_recipe_id",
            columnNames = {"member_id", "recipe_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 후기 목록 조회 시 작성자 닉네임을 함께 표시해야 하므로 Member와 연관관계 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 타 도메인(Recipe)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "content", length = 100, nullable = false)
    private String content;

    @Builder
    private RecipeReview(
        Member member,
        Long recipeId,
        int rating,
        String content
    ) {
        this.member = member;
        this.recipeId = recipeId;
        this.rating = rating;
        this.content = content;
    }

    // 후기 별점 및 내용 수정
    public void update(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }
}

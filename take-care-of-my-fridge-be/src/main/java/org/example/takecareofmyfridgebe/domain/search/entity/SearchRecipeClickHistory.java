package org.example.takecareofmyfridgebe.domain.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

// 검색 결과 레시피 클릭 기록. 클릭 시각은 created_at이다.
// 상품 클릭(search_click_histories)은 할인 분석이 읽으므로 섞지 않고 따로 둔다.
@Entity
@Table(
        name = "search_recipe_click_histories",
        indexes = {
                @Index(name = "idx_search_recipe_click_histories_created_at_recipe_id", columnList = "created_at, recipe_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchRecipeClickHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    // 비로그인 클릭은 null
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Builder
    private SearchRecipeClickHistory(Long recipeId, Long memberId, String keyword) {
        this.recipeId = recipeId;
        this.memberId = memberId;
        this.keyword = keyword;
    }
}

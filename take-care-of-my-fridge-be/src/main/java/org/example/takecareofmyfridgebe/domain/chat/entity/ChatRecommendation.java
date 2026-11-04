package org.example.takecareofmyfridgebe.domain.chat.entity;

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
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

/**
 * AI 상담 답변(AI 메시지)에 붙는 레시피·상품 추천. 레시피 추천 카드는 display_order 순서로 보여 준다.
 */
@Entity
@Table(
    name = "chat_recommendations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_chat_recommendations_chat_message_id_display_order",
            columnNames = {"chat_message_id", "display_order"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRecommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage message;

    // 타 도메인(Recipe)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "recipe_id", nullable = true)
    private Long recipeId;

    // 타 도메인(Product)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "product_id", nullable = true)
    private Long productId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 카드 아래 AI 설명 (레시피 추천만, 상품 추천·표시를 빼먹은 답은 null)
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder
    private ChatRecommendation(
        ChatMessage message,
        Long recipeId,
        Long productId,
        int displayOrder,
        String description
    ) {
        validateSingleTarget(recipeId, productId);
        this.message = message;
        this.recipeId = recipeId;
        this.productId = productId;
        this.displayOrder = displayOrder;
        this.description = description;
    }

    // recipeId와 productId 중 정확히 하나만 존재해야 함
    private static void validateSingleTarget(Long recipeId, Long productId) {
        if ((recipeId == null) == (productId == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}

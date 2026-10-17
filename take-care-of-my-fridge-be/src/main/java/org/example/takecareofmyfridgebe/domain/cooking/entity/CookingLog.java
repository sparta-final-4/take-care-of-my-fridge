package org.example.takecareofmyfridgebe.domain.cooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "cooking_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CookingLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 타 도메인(Member)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 타 도메인(Recipe)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    // 타 도메인(ChatSession)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "chat_session_id", nullable = true)
    private Long chatSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private CookingResult result;

    @Column(name = "memo", length = 50, nullable = true)
    private String memo;

    @Builder
    private CookingLog(
        Long memberId,
        Long recipeId,
        Long chatSessionId,
        CookingResult result,
        String memo
    ) {
        this.memberId = memberId;
        this.recipeId = recipeId;
        this.chatSessionId = chatSessionId;
        this.result = result;
        this.memo = memo;
    }
}

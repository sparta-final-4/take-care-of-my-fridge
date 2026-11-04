package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatRecommendationRepository extends JpaRepository<ChatRecommendation, Long> {

    // AI 메시지 여러 개의 레시피 추천을 한 번에 (메시지 id, 보여 줄 순서대로)
    @Query("SELECT r FROM ChatRecommendation r WHERE r.message.id IN :messageIds AND r.recipeId IS NOT NULL "
            + "ORDER BY r.message.id ASC, r.displayOrder ASC")
    List<ChatRecommendation> findRecipeRecommendationsByMessageIds(@Param("messageIds") Collection<Long> messageIds);

    // 한 상담에서 추천한 레시피 ID
    @Query("SELECT DISTINCT r.recipeId FROM ChatRecommendation r WHERE r.message.session.id = :sessionId AND r.recipeId IS NOT NULL")
    List<Long> findRecipeIdsBySessionId(@Param("sessionId") Long sessionId);
}

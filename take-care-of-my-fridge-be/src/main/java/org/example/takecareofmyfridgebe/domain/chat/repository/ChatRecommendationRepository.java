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

    // 상담 여러 개의 레시피 추천을 한 번에 (상담별, 최근 답부터, 카드 순서). 메시지는 함께 읽는다
    @Query("SELECT r FROM ChatRecommendation r JOIN FETCH r.message m WHERE m.session.id IN :sessionIds AND r.recipeId IS NOT NULL "
            + "ORDER BY m.session.id ASC, m.sequence DESC, r.displayOrder ASC")
    List<ChatRecommendation> findRecipeRecommendationsBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    // 한 상담에서 추천한 레시피 ID
    @Query("SELECT DISTINCT r.recipeId FROM ChatRecommendation r WHERE r.message.session.id = :sessionId AND r.recipeId IS NOT NULL")
    List<Long> findRecipeIdsBySessionId(@Param("sessionId") Long sessionId);

    // 한 상담에서 이 레시피를 레시피 카드로 추천한 적 있는지 (요리 완료 연결)
    @Query("SELECT COUNT(r) > 0 FROM ChatRecommendation r WHERE r.message.session.id = :sessionId AND r.recipeId = :recipeId")
    boolean existsRecipeInSession(@Param("sessionId") Long sessionId, @Param("recipeId") Long recipeId);

    // AI 메시지 여러 개의 레시피·밀키트 추천을 한 번에 (메시지 id, 보여 줄 순서대로)
    @Query("SELECT r FROM ChatRecommendation r WHERE r.message.id IN :messageIds "
            + "ORDER BY r.message.id ASC, r.displayOrder ASC")
    List<ChatRecommendation> findAllByMessageIds(@Param("messageIds") Collection<Long> messageIds);

    // 한 상담에서 보여 준 밀키트 상품 ID
    @Query("SELECT DISTINCT r.productId FROM ChatRecommendation r WHERE r.message.session.id = :sessionId AND r.productId IS NOT NULL")
    List<Long> findProductIdsBySessionId(@Param("sessionId") Long sessionId);
}

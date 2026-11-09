package org.example.takecareofmyfridgebe.domain.cooking.repository;

import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CookingLogRepository extends JpaRepository<CookingLog, Long>, CookingLogRepositoryCustom {

    Optional<CookingLog> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdAndRecipeId(Long memberId, Long recipeId);

    // 상담과 이은 요리 기록 (상담 하나에 보통 하나, 여럿이면 먼저 한 것)
    Optional<CookingLog> findFirstByMemberIdAndChatSessionIdOrderByIdAsc(Long memberId, Long chatSessionId);
}

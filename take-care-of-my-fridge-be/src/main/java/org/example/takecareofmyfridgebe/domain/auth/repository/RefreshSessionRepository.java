package org.example.takecareofmyfridgebe.domain.auth.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    List<RefreshSession> findAllByMember_IdAndIdNotAndRevokedAtIsNull(Long memberId, Long currentSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s join fetch s.member where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}

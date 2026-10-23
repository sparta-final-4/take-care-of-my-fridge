package org.example.takecareofmyfridgebe.domain.refund.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    // 같은 환불의 상태 변경이 동시에 들어와도 한 번만 바뀌도록 행 락을 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);
}

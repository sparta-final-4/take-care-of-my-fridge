package org.example.takecareofmyfridgebe.domain.sale.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleEventRepository extends JpaRepository<SaleEvent, Long>, SaleEventRepositoryCustom {

    // 수정과 상태 정리(시작·종료)가 겹쳐 시작된 행사가 수정되지 않도록 행을 잠그고 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SaleEvent e where e.id = :id")
    Optional<SaleEvent> findByIdForUpdate(@Param("id") Long id);

    // openDue가 열 행사 중 한 종류의 ID. 조건을 바꾸면 openDue도 같이 바꾼다.
    @Query("""
        select e.id from SaleEvent e
        where e.type = :type and e.status = :from and e.startsAt <= :now and e.endsAt > :now
        """)
    List<Long> findIdsDueToOpen(
        @Param("type") SaleEventType type,
        @Param("from") SaleEventStatus from,
        @Param("now") LocalDateTime now
    );

    // 시작 시각이 된 예정 행사를 진행 중으로 바꾼다. 종료 시각까지 지난 행사는 열지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SaleEvent e set e.status = :to
        where e.status = :from and e.startsAt <= :now and e.endsAt > :now
        """)
    int openDue(
        @Param("from") SaleEventStatus from,
        @Param("to") SaleEventStatus to,
        @Param("now") LocalDateTime now
    );

    // 종료 시각이 지난 행사를 종료로 바꾼다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SaleEvent e set e.status = :to
        where e.status in :from and e.endsAt <= :now
        """)
    int closeDue(
        @Param("from") Collection<SaleEventStatus> from,
        @Param("to") SaleEventStatus to,
        @Param("now") LocalDateTime now
    );
}

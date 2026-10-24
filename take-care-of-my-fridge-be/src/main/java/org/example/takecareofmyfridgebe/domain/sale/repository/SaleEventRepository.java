package org.example.takecareofmyfridgebe.domain.sale.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleEventRepository extends JpaRepository<SaleEvent, Long>, SaleEventRepositoryCustom {

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

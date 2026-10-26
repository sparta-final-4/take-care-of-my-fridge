package org.example.takecareofmyfridgebe.domain.forecast.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForecastSalesRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
        select new org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales(
            oi.productId, cast(o.confirmedAt as LocalDate), sum(oi.quantity))
        from OrderItem oi join oi.order o
        where o.confirmedAt >= :from and o.confirmedAt < :to
          and o.confirmedAt <= :asOf
        group by oi.productId, cast(o.confirmedAt as LocalDate)
        """)
    List<DailyNetSales> findConfirmedSales(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("asOf") LocalDateTime asOf);

    @Query("""
        select new org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales(
            oi.productId, cast(o.confirmedAt as LocalDate), sum(ri.quantity))
        from RefundItem ri join ri.refund r join ri.orderItem oi join oi.order o
        where o.confirmedAt >= :from and o.confirmedAt < :to
          and o.confirmedAt <= :asOf
          and r.status = :status
          and r.refundedAt <= :asOf
        group by oi.productId, cast(o.confirmedAt as LocalDate)
        """)
    List<DailyNetSales> findCompletedRefunds(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             @Param("asOf") LocalDateTime asOf,
                                             @Param("status") RefundStatus status);
}

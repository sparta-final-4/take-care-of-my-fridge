package org.example.takecareofmyfridgebe.domain.order.repository;

import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    // 구매 확정된 주문만, confirmedAt이 [from, to) 범위인 것을 날짜·상품·판매유형별로 합산
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales(
            oi.productId, cast(o.confirmedAt as LocalDate), oi.saleType, sum(oi.quantity))
        from OrderItem oi join oi.order o
        where o.status = :status
          and o.confirmedAt >= :from and o.confirmedAt < :to
        group by oi.productId, cast(o.confirmedAt as LocalDate), oi.saleType
        order by cast(o.confirmedAt as LocalDate), oi.productId, oi.saleType
        """)
    List<DailyProductSales> findDailyProductSales(
        @Param("status") OrderStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}

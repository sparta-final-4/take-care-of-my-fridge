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

    // 이 회원이 이미 산(취소하지 않은) 번개 드롭인지 확인한다
    boolean existsBySaleEventItemIdAndFlashDropMemberId(Long saleEventItemId, Long flashDropMemberId);

    // 구매 확정된 주문만, confirmedAt이 [from, to) 범위이고 asOf 이전인 것의 확정 수량을 날짜·상품별로 합산
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales(
            oi.productId, cast(o.confirmedAt as LocalDate), sum(oi.confirmedQuantity))
        from OrderItem oi join oi.order o
        where o.status = :status
          and o.confirmedAt >= :from and o.confirmedAt < :to
          and o.confirmedAt <= :asOf
        group by oi.productId, cast(o.confirmedAt as LocalDate)
        order by cast(o.confirmedAt as LocalDate), oi.productId
        """)
    List<DailyProductSales> findDailyProductSales(
        @Param("status") OrderStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("asOf") LocalDateTime asOf
    );

    // 평상시 기준선: 할인 행사에 속하지 않은 구매 확정 순판매량만 집계한다.
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales(
            oi.productId, cast(o.confirmedAt as LocalDate), sum(oi.confirmedQuantity))
        from OrderItem oi join oi.order o
        where oi.saleEventItemId is null and o.status = :status
          and o.confirmedAt >= :from and o.confirmedAt < :to
          and o.confirmedAt <= :asOf
        group by oi.productId, cast(o.confirmedAt as LocalDate)
        order by cast(o.confirmedAt as LocalDate), oi.productId
        """)
    List<DailyProductSales> findDailyRegularProductSales(
        @Param("status") OrderStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("asOf") LocalDateTime asOf
    );

    // 행사 상품에 연결된 구매 확정 후 환불 차감 수량. 기준 시각 뒤의 확정은 제외한다.
    @Query("""
        select coalesce(sum(oi.confirmedQuantity), 0)
        from OrderItem oi join oi.order o
        where oi.saleEventItemId = :saleEventItemId and o.status = :status
          and o.confirmedAt <= :asOf
        """)
    long sumConfirmedSaleQuantity(@Param("saleEventItemId") Long saleEventItemId,
                                  @Param("status") OrderStatus status,
                                  @Param("asOf") LocalDateTime asOf);

    // 미확정 결제 주문이 남은 행사는 실제 판매 수량이 아직 완결되지 않았다.
    @Query("""
        select count(oi) from OrderItem oi join oi.order o
        where oi.saleEventItemId = :saleEventItemId and o.status = :status
          and o.paidAt <= :asOf
        """)
    long countUnconfirmedPaidSaleOrders(@Param("saleEventItemId") Long saleEventItemId,
                                        @Param("status") OrderStatus status,
                                        @Param("asOf") LocalDateTime asOf);
}

package org.example.takecareofmyfridgebe.domain.refund.repository;

import org.example.takecareofmyfridgebe.domain.refund.dto.RefundedQuantity;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {

    @Query("select ri from RefundItem ri join fetch ri.orderItem where ri.refund.id = :refundId")
    List<RefundItem> findWithOrderItemByRefundId(@Param("refundId") Long refundId);

    @Query("select ri from RefundItem ri join fetch ri.orderItem where ri.refund.id in :refundIds order by ri.id")
    List<RefundItem> findWithOrderItemByRefundIdIn(@Param("refundIds") Collection<Long> refundIds);

    // 주문 상품별로 statuses 상태인 환불의 수량을 합산한다. 환불이 없는 주문 상품은 결과에 없다
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.refund.dto.RefundedQuantity(
            ri.orderItem.id, sum(ri.quantity))
        from RefundItem ri join ri.refund r
        where ri.orderItem.id in :orderItemIds
          and r.status in :statuses
        group by ri.orderItem.id
        """)
    List<RefundedQuantity> findRefundedQuantitiesByOrderItemIds(
        @Param("orderItemIds") Collection<Long> orderItemIds,
        @Param("statuses") Collection<RefundStatus> statuses
    );
}

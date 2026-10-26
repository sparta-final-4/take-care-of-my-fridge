package org.example.takecareofmyfridgebe.domain.refund.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundAmountByStatus;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    // 같은 환불의 상태 변경이 동시에 들어와도 한 번만 바뀌도록 행 락을 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);

    // 주문의 모든 결제에 걸린 환불을 최신순으로 찾는다
    @Query("select r from Refund r where r.payment.order.id = :orderId order by r.id desc")
    List<Refund> findByOrderIdOrderByIdDesc(@Param("orderId") Long orderId);

    @Query("select count(r) > 0 from Refund r where r.payment.order.id = :orderId and r.status = :status")
    boolean existsByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") RefundStatus status);

    @Query("select r from Refund r where r.payment.id = :paymentId and r.status = :status order by r.id")
    List<Refund> findByPaymentIdAndStatus(@Param("paymentId") Long paymentId, @Param("status") RefundStatus status);

    @Query("select coalesce(sum(r.refundAmount), 0) from Refund r where r.payment.id = :paymentId and r.status = :status")
    long sumAmountByPaymentIdAndStatus(@Param("paymentId") Long paymentId, @Param("status") RefundStatus status);

    @Query("select r.portoneCancellationId from Refund r where r.payment.id = :paymentId and r.portoneCancellationId is not null")
    List<String> findCancellationIdsByPaymentId(@Param("paymentId") Long paymentId);

    // 스케줄러가 정리할, before 전에 신청돼 아직 요청 상태인 환불의 PortOne 결제 ID
    @Query("select distinct r.payment.portonePaymentId from Refund r where r.status = :status and r.createdAt < :before")
    List<String> findPortonePaymentIdsByStatusAndCreatedAtBefore(@Param("status") RefundStatus status,
                                                                 @Param("before") LocalDateTime before);

    // 환불 요약. 회원의 주문만 골라 주문 상품을 읽는다 (주문 도메인을 고치지 않으려고 환불 쪽에 둔다)
    @Query("select oi from OrderItem oi join fetch oi.order o where o.id in :orderIds and o.memberId = :memberId order by oi.id")
    List<OrderItem> findOwnedOrderItems(@Param("memberId") Long memberId, @Param("orderIds") Collection<Long> orderIds);

    @Query("""
        select new org.example.takecareofmyfridgebe.domain.refund.dto.RefundAmountByStatus(
            r.payment.order.id, r.status, sum(r.refundAmount))
        from Refund r
        where r.payment.order.id in :orderIds
        group by r.payment.order.id, r.status
        """)
    List<RefundAmountByStatus> sumAmountByOrderIdsGroupByStatus(@Param("orderIds") Collection<Long> orderIds);
}

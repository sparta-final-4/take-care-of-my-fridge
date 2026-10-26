package org.example.takecareofmyfridgebe.domain.payment.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.order where p.portonePaymentId = :portonePaymentId")
    Optional<Payment> findWithOrderByPortonePaymentId(@Param("portonePaymentId") String portonePaymentId);

    @Query("select p from Payment p where p.order.id = :orderId and p.status = :status order by p.id")
    List<Payment> findByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") PaymentStatus status);

    // 확정 요청과 웹훅이 동시에 들어와도 한 번만 상태가 바뀌도록 행 락을 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.portonePaymentId = :portonePaymentId")
    Optional<Payment> findWithOrderByPortonePaymentIdForUpdate(@Param("portonePaymentId") String portonePaymentId);

    // 만료 시각이 지난 결제를 id 순으로 나눠 읽는다. 처리에 실패해 남은 건이 뒤의 건을 막지 않도록 id 커서를 쓴다
    @Query("select p from Payment p where p.status = :status and p.expiresAt < :now and p.id > :afterId order by p.id")
    List<Payment> findExpired(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now,
                              @Param("afterId") long afterId, Pageable pageable);

    // 실패 처리했는데 PG에서 결제된 기록(PAID)만 있고 보상 취소 기록(CANCELLED)이 없는 결제를 id 순으로 나눠 읽는다
    @Query("""
        select p from Payment p
        where p.status = :status and p.id > :afterId
          and exists (select h.id from PaymentHistory h where h.payment = p and h.eventType = :paidEvent)
          and not exists (select h.id from PaymentHistory h where h.payment = p and h.eventType = :cancelledEvent)
        order by p.id
        """)
    List<Payment> findCompensationPending(@Param("status") PaymentStatus status,
                                          @Param("paidEvent") PaymentEventType paidEvent,
                                          @Param("cancelledEvent") PaymentEventType cancelledEvent,
                                          @Param("afterId") long afterId, Pageable pageable);
}

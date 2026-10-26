package org.example.takecareofmyfridgebe.domain.payment.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    boolean existsByPaymentIdAndEventType(Long paymentId, PaymentEventType eventType);

    // 환불이 아닌 취소(콘솔에서 직접 취소 등)의 금액 합
    @Query("""
        select coalesce(sum(h.amount), 0) from PaymentHistory h
        where h.payment.id = :paymentId and h.refundId is null and h.eventType in :eventTypes
        """)
    long sumAmountWithoutRefund(@Param("paymentId") Long paymentId,
                                @Param("eventTypes") Collection<PaymentEventType> eventTypes);

    @Query("select h.pgCancellationId from PaymentHistory h where h.payment.id = :paymentId and h.pgCancellationId is not null")
    List<String> findPgCancellationIds(@Param("paymentId") Long paymentId);
}

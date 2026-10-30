package org.example.takecareofmyfridgebe.domain.settlement.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

// 정산 집계가 결제 이력을 읽기만 하는 조회 전용 리포지토리. 결제·환불 코드는 고치지 않는다
public interface SalesHistoryRepository extends Repository<PaymentHistory, Long> {

    // from 이상 to 미만에 남은 결제·취소 이력의 합. 결제 상태가 paymentStatus인 결제만 세어
    // 받지 않은 결제(실패 처리 후 PG 승인)와 그 보상 취소는 빠진다
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum(
            coalesce(sum(case when h.eventType = :paidEvent then h.amount else 0 end), 0),
            coalesce(sum(case when h.eventType = :paidEvent then 1 else 0 end), 0),
            coalesce(sum(case when h.eventType in :cancelEvents then h.amount else 0 end), 0))
        from PaymentHistory h
        where h.payment.status = :paymentStatus and h.createdAt >= :from and h.createdAt < :to
        """)
    DailySalesSum sumBetween(@Param("paymentStatus") PaymentStatus paymentStatus,
                             @Param("paidEvent") PaymentEventType paidEvent,
                             @Param("cancelEvents") Collection<PaymentEventType> cancelEvents,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

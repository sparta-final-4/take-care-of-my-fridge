package org.example.takecareofmyfridgebe.domain.settlement.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// 정산 집계가 결제 이력을 읽기만 하는 조회 전용 리포지토리. 결제·환불 코드는 고치지 않는다
public interface SalesHistoryRepository extends Repository<PaymentHistory, Long> {

    // from 이상 to 미만의 매출. 결제 완료(PAID)된 결제의 결제 금액·건수와 부분·전액 취소(환불·콘솔 취소) 금액.
    // 결제 상태가 PAID인 결제만 세어 받지 않은 결제(실패 처리 후 PG 승인)와 그 보상 취소는 빠진다
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum(
            coalesce(sum(case when h.eventType = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID
                then h.amount else 0 end), 0),
            coalesce(sum(case when h.eventType = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID
                then 1 else 0 end), 0),
            coalesce(sum(case when h.eventType in (
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PARTIAL_CANCELLED,
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.CANCELLED)
                then h.amount else 0 end), 0))
        from PaymentHistory h
        where h.payment.status = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus.PAID
          and h.createdAt >= :from and h.createdAt < :to
        """)
    DailySalesSum sumSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

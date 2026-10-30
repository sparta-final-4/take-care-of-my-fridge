package org.example.takecareofmyfridgebe.domain.settlement.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.dto.PaymentSalesRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// 정산 집계가 결제 이력을 읽기만 하는 조회 전용 리포지토리. 결제·환불 코드는 고치지 않는다
public interface SalesHistoryRepository extends Repository<PaymentHistory, Long> {

    // from 이상 to 미만의 매출. 결제 완료(PAID)된 결제의 결제 금액·건수와 부분·전액 취소(환불·콘솔 취소) 금액.
    // 결제 상태가 PAID인 결제만 세어 받지 않은 결제(실패 처리 후 PG 승인)와 그 보상 취소는 빠진다.
    // 환불 건수는 부분·전액 취소 이력 한 건마다 1이다
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum(
            coalesce(sum(case when h.eventType = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID
                then h.amount else 0 end), 0),
            coalesce(sum(case when h.eventType = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID
                then 1 else 0 end), 0),
            coalesce(sum(case when h.eventType in (
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PARTIAL_CANCELLED,
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.CANCELLED)
                then h.amount else 0 end), 0),
            coalesce(sum(case when h.eventType in (
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PARTIAL_CANCELLED,
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.CANCELLED)
                then 1 else 0 end), 0))
        from PaymentHistory h
        where h.payment.status = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus.PAID
          and h.createdAt >= :from and h.createdAt < :to
        """)
    DailySalesSum sumSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 위 합계를 결제 한 건씩 나눈 목록. 같은 조건으로 세므로 모든 행을 더하면 sumSalesBetween과 같다.
    // 기간 전에 결제하고 기간 안에 환불한 결제는 결제 금액 0으로 들어온다.
    // 기간 안의 마지막 결제·환불이 최근인 결제가 먼저 온다. 지난달 결제를 오늘 환불했으면 맨 위에 보인다
    @Query(value = """
        select new org.example.takecareofmyfridgebe.domain.settlement.dto.PaymentSalesRow(
            p.id, p.paidAt, o.orderNumber, m.nickname,
            coalesce(sum(case when h.eventType = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID
                then h.amount else 0 end), 0),
            coalesce(sum(case when h.eventType in (
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PARTIAL_CANCELLED,
                    org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.CANCELLED)
                then h.amount else 0 end), 0))
        from PaymentHistory h
        join h.payment p
        join p.order o
        left join Member m on m.id = o.memberId
        where p.status = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus.PAID
          and h.createdAt >= :from and h.createdAt < :to
          and h.eventType in (
              org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID,
              org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PARTIAL_CANCELLED,
              org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.CANCELLED)
        group by p.id, p.paidAt, o.orderNumber, m.nickname
        order by max(h.createdAt) desc, p.id desc
        """,
            countQuery = """
        select count(distinct p.id)
        from PaymentHistory h
        join h.payment p
        join p.order o
        where p.status = org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus.PAID
          and h.createdAt >= :from and h.createdAt < :to
          and h.eventType in (
              org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PAID,
              org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.PARTIAL_CANCELLED,
              org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType.CANCELLED)
        """)
    Page<PaymentSalesRow> findPaymentSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                  Pageable pageable);
}

package org.example.takecareofmyfridgebe.domain.payment.dto;

// 기간 안에 결제 완료된 결제의 금액 합과 건수. 정산 집계가 결제 도메인에서 받아 간다
public record PaidSalesSum(
        long paidAmount,
        long paidCount
) {
}

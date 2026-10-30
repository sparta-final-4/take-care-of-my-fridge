package org.example.takecareofmyfridgebe.domain.settlement.dto;

// 결제 이력으로 센 기간 합계. refundedAmount는 환불과 PG 콘솔 취소를 모두 포함한다
public record DailySalesSum(
        long paidAmount,
        long paidCount,
        long refundedAmount
) {
}

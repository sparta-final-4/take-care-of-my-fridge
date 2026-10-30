package org.example.takecareofmyfridgebe.domain.settlement.dto;

// 결제 이력으로 센 기간 합계. 환불 금액·건수는 환불과 PG 콘솔 취소를 모두 포함하고, 부분 취소도 한 건으로 센다
public record DailySalesSum(
        long paidAmount,
        long paidCount,
        long refundedAmount,
        long refundedCount
) {
}

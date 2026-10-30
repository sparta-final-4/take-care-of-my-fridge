package org.example.takecareofmyfridgebe.domain.settlement.dto;

// 결제 이력으로 센 기간 합계. 환불 금액·건수는 환불과 PG 콘솔 취소를 모두 포함한다.
// 환불 건수는 결제 수가 아니라 취소 이력 한 건을 1건으로 센다(같은 결제를 두 번 부분 환불하면 2건)
public record DailySalesSum(
        long paidAmount,
        long paidCount,
        long refundedAmount,
        long refundedCount
) {
}

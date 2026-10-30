package org.example.takecareofmyfridgebe.domain.settlement.dto;

import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;

// 위쪽 카드, 정산 표 합계 행, 오늘 매출에 함께 쓰는 금액 묶음. 순매출은 음수일 수 있다
public record SalesAmountResponse(
        long paidAmount,
        long refundedAmount,
        long netAmount,
        int paidCount
) {
    public static SalesAmountResponse of(long paidAmount, long refundedAmount, int paidCount) {
        return new SalesAmountResponse(paidAmount, refundedAmount, paidAmount - refundedAmount, paidCount);
    }

    public static SalesAmountResponse from(DailySales dailySales) {
        return of(dailySales.getPaidAmount(), dailySales.getRefundedAmount(), dailySales.getPaidCount());
    }

    // 아직 저장하지 않은 날(오늘)의 매출을 결제 이력 합계로 바로 만든다
    public static SalesAmountResponse from(DailySalesSum sum) {
        return of(sum.paidAmount(), sum.refundedAmount(), Math.toIntExact(sum.paidCount()));
    }
}

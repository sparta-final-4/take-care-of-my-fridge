package org.example.takecareofmyfridgebe.domain.settlement.dto;

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
}

package org.example.takecareofmyfridgebe.domain.payment.dto;

// 결제 승인 결과. 승인하지 않았으면 response 없이 거절 이유만 담는다
public record PaymentApproveResult(
        PaymentConfirmResponse response,
        Rejection rejection
) {
    public enum Rejection {
        PAYMENT_ALREADY_FAILED, // 결제가 이미 실패 처리됨(창 닫기·만료 등과 경합)
        ORDER_NOT_PAYABLE       // 주문이 이미 다른 결제로 결제 완료됐거나 취소됨
    }

    public static PaymentApproveResult approved(PaymentConfirmResponse response) {
        return new PaymentApproveResult(response, null);
    }

    public static PaymentApproveResult rejected(Rejection rejection) {
        return new PaymentApproveResult(null, rejection);
    }

    public boolean isApproved() {
        return rejection == null;
    }
}

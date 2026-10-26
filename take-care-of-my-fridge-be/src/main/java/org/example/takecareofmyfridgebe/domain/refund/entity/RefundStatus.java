package org.example.takecareofmyfridgebe.domain.refund.entity;

import java.util.List;

public enum RefundStatus {
    REQUESTED, // 환불 요청됨 (PG 취소 대기)
    COMPLETED, // PG 취소 완료
    FAILED;    // PG 취소 실패

    // 처리 중이거나 끝난 환불의 수량만 누적에 넣는다. 실패한 환불 수량은 다시 신청할 수 있다
    public static final List<RefundStatus> COUNTED = List.of(REQUESTED, COMPLETED);
}

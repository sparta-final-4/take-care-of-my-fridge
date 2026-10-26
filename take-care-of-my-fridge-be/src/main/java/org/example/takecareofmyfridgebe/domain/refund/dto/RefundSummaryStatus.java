package org.example.takecareofmyfridgebe.domain.refund.dto;

// 주문 단위 환불 상태. 저장하지 않고 환불 기록으로 계산한다
public enum RefundSummaryStatus {
    NONE,        // 완료된 환불 없음 (특가·번개 드롭 상품만 있는 주문 포함)
    IN_PROGRESS, // 진행 중(REQUESTED)인 환불이 있음
    PARTIAL,     // 완료된 환불이 있지만 환불 가능한 상품이 남음
    FULL         // 환불 가능한 상품(특가·번개 드롭 제외)을 모두 환불함
}

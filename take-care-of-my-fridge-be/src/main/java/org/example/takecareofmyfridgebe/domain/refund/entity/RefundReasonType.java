package org.example.takecareofmyfridgebe.domain.refund.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// description은 PG 취소 사유로 보낸다
@Getter
@RequiredArgsConstructor
public enum RefundReasonType {
    CHANGE_OF_MIND("단순 변심"),
    ORDER_MISTAKE("주문 실수"),
    NO_LONGER_NEEDED("더 이상 필요 없음"),
    ETC("기타");

    private final String description;
}

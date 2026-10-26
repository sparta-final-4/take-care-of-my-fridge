package org.example.takecareofmyfridgebe.domain.sale.entity;

import java.util.List;

public enum SaleEventStatus {
    SCHEDULED,
    ACTIVE,
    ENDED;

    // 종료로 닫지 않은 상태. 한 상품은 이 상태의 행사 하나에만 들어간다.
    public static final List<SaleEventStatus> OPEN = List.of(SCHEDULED, ACTIVE);
}

package org.example.takecareofmyfridgebe.domain.order.entity;

import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

public enum SaleType {
    NONE,
    SPECIAL,
    FLASH_DROP;

    // 진행 중 할인이 없으면(null) NONE이다
    public static SaleType from(SaleEventType type) {
        return type == null ? NONE : valueOf(type.name());
    }
}

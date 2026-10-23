package org.example.takecareofmyfridgebe.domain.order.entity;

import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

public enum SaleType {
    NONE,
    SPECIAL,
    FLASH_DROP;

    // 진행 중 할인이 없으면(null) NONE이다. 행사 구분이 늘면 여기서 컴파일 오류로 알 수 있도록 switch로 옮긴다.
    public static SaleType from(SaleEventType type) {
        if (type == null) {
            return NONE;
        }
        return switch (type) {
            case SPECIAL -> SPECIAL;
            case FLASH_DROP -> FLASH_DROP;
        };
    }
}

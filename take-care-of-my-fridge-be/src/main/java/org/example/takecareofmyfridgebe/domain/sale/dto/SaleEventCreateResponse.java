package org.example.takecareofmyfridgebe.domain.sale.dto;

import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;

public record SaleEventCreateResponse(
        Long saleEventId
) {
    public static SaleEventCreateResponse from(SaleEvent saleEvent) {
        return new SaleEventCreateResponse(saleEvent.getId());
    }
}

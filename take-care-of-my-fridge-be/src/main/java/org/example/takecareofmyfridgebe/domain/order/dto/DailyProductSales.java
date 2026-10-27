package org.example.takecareofmyfridgebe.domain.order.dto;

import java.time.LocalDate;

public record DailyProductSales(Long productId, LocalDate salesDate, long quantity) {
}

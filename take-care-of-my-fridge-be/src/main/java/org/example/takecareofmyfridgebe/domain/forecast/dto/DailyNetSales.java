package org.example.takecareofmyfridgebe.domain.forecast.dto;

import java.time.LocalDate;

public record DailyNetSales(Long productId, LocalDate salesDate, long quantity) {
}

package org.example.takecareofmyfridgebe.domain.deal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;

public record DealAnalysisRunRequest(
        @NotNull LocalDate targetWeekStart,
        @NotNull DealAnalysis.Day dayOfWeek,
        @NotNull @Min(1) @Max(99) Integer discountRate) {
}

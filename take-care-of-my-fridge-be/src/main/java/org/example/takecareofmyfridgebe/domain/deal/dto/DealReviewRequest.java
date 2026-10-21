package org.example.takecareofmyfridgebe.domain.deal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;

public record DealReviewRequest(@NotEmpty List<@Valid Review> reviews) {

    public record Review(
            @NotNull @Positive Long candidateId,
            @NotNull DealCandidate.ReviewStatus reviewStatus
    ) {}
}

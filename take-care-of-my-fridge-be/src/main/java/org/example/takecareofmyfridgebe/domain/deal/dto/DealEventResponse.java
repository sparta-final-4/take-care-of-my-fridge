package org.example.takecareofmyfridgebe.domain.deal.dto;

import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;

public record DealEventResponse(Long candidateId, Long saleEventId,
                                DealCandidate.EventRequestStatus eventRequestStatus) {
    public static DealEventResponse from(DealCandidate candidate) {
        return new DealEventResponse(candidate.getId(), candidate.getPromotionId(),
                candidate.getEventRequestStatus());
    }
}

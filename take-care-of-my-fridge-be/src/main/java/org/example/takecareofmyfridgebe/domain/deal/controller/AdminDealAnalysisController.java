package org.example.takecareofmyfridgebe.domain.deal.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventResponse;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewResponse;
import org.example.takecareofmyfridgebe.domain.deal.facade.DealReviewFacade;
import org.example.takecareofmyfridgebe.domain.deal.facade.DealEventFacade;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisReadService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/deal-analyses")
@RequiredArgsConstructor
public class AdminDealAnalysisController {

    private final DealAnalysisReadService readService;
    private final DealReviewFacade reviewFacade;
    private final DealEventFacade eventFacade;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<DealAnalysisResponse>> getLatest(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(readService.getLatestForMember(principal.memberId())));
    }

    @GetMapping("/{analysisId}")
    public ResponseEntity<ApiResponse<DealAnalysisResponse>> getById(@PathVariable Long analysisId) {
        return ResponseEntity.ok(ApiResponse.ok(readService.getById(analysisId)));
    }

    @PutMapping("/{analysisId}/reviews")
    public ResponseEntity<ApiResponse<DealReviewResponse>> saveReviews(
            @PathVariable Long analysisId,
            @Valid @RequestBody DealReviewRequest request,
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(reviewFacade.save(
                principal.memberId(), principal.role(), analysisId, request.reviews())));
    }

    @PostMapping("/{analysisId}/event-requests")
    public ResponseEntity<ApiResponse<DealEventResponse>> registerEvent(
            @PathVariable Long analysisId,
            @Valid @RequestBody DealEventRequest request,
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(eventFacade.register(
                principal.memberId(), principal.role(), analysisId, request)));
    }
}

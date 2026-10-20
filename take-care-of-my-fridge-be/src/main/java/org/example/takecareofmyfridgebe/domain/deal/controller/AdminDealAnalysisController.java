package org.example.takecareofmyfridgebe.domain.deal.controller;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisReadService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/deal-analyses")
@RequiredArgsConstructor
public class AdminDealAnalysisController {

    private final DealAnalysisReadService readService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<DealAnalysisResponse>> getLatest(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(readService.getLatestForMember(principal.memberId())));
    }

    @GetMapping("/{analysisId}")
    public ResponseEntity<ApiResponse<DealAnalysisResponse>> getById(@PathVariable Long analysisId) {
        return ResponseEntity.ok(ApiResponse.ok(readService.getById(analysisId)));
    }
}

package org.example.takecareofmyfridgebe.domain.review.controller;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSummaryResult;
import org.example.takecareofmyfridgebe.domain.review.facade.ReviewSummaryFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/review-summaries")
public class ReviewSummaryAdminController {

    private final ReviewSummaryFacade reviewSummaryFacade;

    /**
     * 관리자가 수동으로 레시피 후기 요약 새벽 작업을 즉시 실행한다.
     * 새벽 스케줄러와 동일한 분산 락(reviewSummary)을 사용하여 중복 실행을 방지하며,
     * 이미 작업이 진행 중인 경우 409 Conflict(REVIEW_SUMMARY_RUNNING)를 반환한다 (ReviewSummaryFacade.runWithLock).
     */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<ReviewSummaryResult>> runReviewSummary() {
        return ResponseEntity.ok(ApiResponse.ok(reviewSummaryFacade.runWithLock()));
    }
}

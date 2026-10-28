package org.example.takecareofmyfridgebe.domain.review.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSummaryResult;
import org.example.takecareofmyfridgebe.domain.review.facade.ReviewSummaryFacade;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/review-summaries")
public class ReviewSummaryAdminController {

    private static final String LOCK_NAME = "reviewSummary";
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);
    /**
     * 관리자 수동 실행은 작업이 끝나면 바로 다시 실행할 수 있도록 lockAtLeastFor를 0으로 설정한다.
     * (정기 스케줄러는 중복 실행 방지를 위해 5분 유지)
     */
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ZERO;

    private final ReviewSummaryFacade reviewSummaryFacade;
    private final LockingTaskExecutor lockingTaskExecutor;

    /**
     * 관리자가 수동으로 레시피 후기 요약 새벽 작업을 즉시 실행한다.
     * 새벽 스케줄러와 동일한 분산 락(reviewSummary)을 사용하여 중복 실행을 방지하며,
     * 이미 작업이 진행 중인 경우 409 Conflict(REVIEW_SUMMARY_RUNNING)를 반환한다.
     */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<ReviewSummaryResult>> runReviewSummary() {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(),
                LOCK_NAME,
                LOCK_AT_MOST_FOR,
                LOCK_AT_LEAST_FOR
        );

        try {
            LockingTaskExecutor.TaskResult<ReviewSummaryResult> taskResult = lockingTaskExecutor.executeWithLock(
                    (LockingTaskExecutor.TaskWithResult<ReviewSummaryResult>) reviewSummaryFacade::runOnce,
                    lockConfiguration
            );

            if (!taskResult.wasExecuted()) {
                throw new BusinessException(ErrorCode.REVIEW_SUMMARY_RUNNING);
            }

            return ResponseEntity.ok(ApiResponse.ok(taskResult.getResult()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("관리자 후기 요약 작업 실행 중 예외 발생", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (Throwable t) {
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }
}

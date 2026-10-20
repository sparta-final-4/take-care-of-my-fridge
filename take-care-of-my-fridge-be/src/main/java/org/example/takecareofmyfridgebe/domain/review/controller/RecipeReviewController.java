package org.example.takecareofmyfridgebe.domain.review.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewUpdateRequest;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class RecipeReviewController {

    private final RecipeReviewService recipeReviewService;

    /**
     * 레시피 후기 목록을 검색 조건(recipeId, mine, sort, page, size)으로 조회한다.
     * 로그인하지 않은 사용자도 조회 가능하며, mine=true인 경우에만 로그인이 요구된다.
     *
     * @param principal 인증된 요청자의 회원 정보 (비로그인 시 null)
     * @param request   조회 필터 및 페이징 조건
     * @return 페이징된 레시피 후기 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getReviews(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute ReviewSearchRequest request
    ) {
        Long currentMemberId = principal != null ? principal.memberId() : null;
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 회원이 레시피에 대한 후기를 작성한다.
     *
     * @param principal 인증된 요청자의 회원 정보
     * @param request   후기 작성 정보 (recipeId, rating, content)
     * @return 작성된 후기 상세 응답
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        ReviewResponse response = recipeReviewService.createReview(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 회원이 본인이 작성한 후기를 수정한다.
     *
     * @param principal 인증된 요청자의 회원 정보
     * @param reviewId  수정할 후기 ID
     * @param request   수정할 후기 정보 (rating, content)
     * @return 수정된 후기 상세 응답
     */
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        ReviewResponse response = recipeReviewService.updateReview(principal.memberId(), reviewId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 회원이 본인이 작성한 후기를 삭제한다.
     *
     * @param principal 인증된 요청자의 회원 정보
     * @param reviewId  삭제할 후기 ID
     * @return 성공 여부 응답
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long reviewId
    ) {
        recipeReviewService.deleteReview(principal.memberId(), reviewId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}

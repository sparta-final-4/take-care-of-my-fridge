import {
  adminReviewSummaryApi,
  recipeReviewApi,
  type PageResponseReviewResponse,
  type ReviewCreateRequest,
  type ReviewResponse,
  type ReviewSummaryItem,
  type ReviewSummaryResult,
  type ReviewSearchRequest,
  type ReviewUpdateRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export type {
  ReviewCreateRequest,
  ReviewResponse,
  ReviewSummaryItem,
  ReviewSummaryResult,
  ReviewSearchRequest,
  ReviewUpdateRequest,
  PageResponseReviewResponse,
};

/**
 * 레시피 후기 목록을 조회한다 (로그인 없이 조회 가능).
 */
export async function getReviews(
  request: ReviewSearchRequest,
  signal?: AbortSignal,
): Promise<PageResponseReviewResponse> {
  return unwrap(recipeReviewApi.getReviews({ request }, { signal }));
}

/**
 * 로그인한 회원이 레시피에 대한 후기를 작성한다.
 */
export async function createReview(request: ReviewCreateRequest): Promise<ReviewResponse> {
  return unwrap(recipeReviewApi.createReview({ reviewCreateRequest: request }));
}

/**
 * 로그인한 회원이 본인이 작성한 후기를 수정한다.
 */
export async function updateReview(
  reviewId: number,
  request: ReviewUpdateRequest,
): Promise<ReviewResponse> {
  return unwrap(recipeReviewApi.updateReview({ reviewId, reviewUpdateRequest: request }));
}

/**
 * 로그인한 회원이 본인이 작성한 후기를 삭제한다.
 */
export async function deleteReview(reviewId: number): Promise<void> {
  return unwrap(recipeReviewApi.deleteReview({ reviewId }));
}

// 관리자 실행은 백엔드가 20초에서 끊고 응답하므로(CloudFront 30초 안), 기본 10초보다 넉넉히 기다린다.
const REVIEW_SUMMARY_RUN_TIMEOUT_MS = 40_000;

/**
 * 관리자가 후기 요약 작업을 바로 실행한다. 결과는 저장되지 않고 이 응답으로만 온다.
 */
export async function runReviewSummary(): Promise<ReviewSummaryResult> {
  return unwrap(adminReviewSummaryApi.runReviewSummary({ timeout: REVIEW_SUMMARY_RUN_TIMEOUT_MS }));
}

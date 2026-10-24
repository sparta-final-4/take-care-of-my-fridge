import {
  recipeReviewApi,
  type PageResponseReviewResponse,
  type ReviewCreateRequest,
  type ReviewResponse,
  type ReviewSearchRequest,
  type ReviewUpdateRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export type {
  ReviewCreateRequest,
  ReviewResponse,
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

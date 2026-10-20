import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';
import { csrfHeaders } from '@/domains/auth/api';

// ReviewCreateRequest.java
export interface ReviewCreateRequest {
  recipeId: number;
  rating: number;
  content: string;
}

// ReviewUpdateRequest.java
export interface ReviewUpdateRequest {
  rating: number;
  content: string;
}

// ReviewResponse.java
export interface ReviewResponse {
  id: number;
  recipeId: number;
  rating: number;
  content: string;
  nickname: string | null;
  mine: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * 로그인한 회원이 레시피에 대한 후기를 작성한다.
 */
export async function createReview(request: ReviewCreateRequest): Promise<ReviewResponse> {
  const { data } = await axiosInstance.post<ApiResponse<ReviewResponse>>('/api/reviews', request, {
    headers: await csrfHeaders(),
  });
  return data.data;
}

/**
 * 로그인한 회원이 본인이 작성한 후기를 수정한다.
 */
export async function updateReview(
  reviewId: number,
  request: ReviewUpdateRequest,
): Promise<ReviewResponse> {
  const { data } = await axiosInstance.patch<ApiResponse<ReviewResponse>>(
    `/api/reviews/${reviewId}`,
    request,
    { headers: await csrfHeaders() },
  );
  return data.data;
}

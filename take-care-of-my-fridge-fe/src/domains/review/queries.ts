import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';

import {
  createReview,
  deleteReview,
  getReviews,
  updateReview,
  type ReviewCreateRequest,
  type ReviewSearchRequest,
  type ReviewUpdateRequest,
} from './api';

export const reviewQueries = {
  all: ['reviews'] as const,
  lists: () => [...reviewQueries.all, 'list'] as const,
  list: (request: ReviewSearchRequest) =>
    queryOptions({
      queryKey: [...reviewQueries.lists(), request],
      queryFn: ({ signal }) => getReviews(request, signal),
    }),
  myReview: (recipeId: number) =>
    queryOptions({
      queryKey: [...reviewQueries.all, 'my', recipeId],
      queryFn: ({ signal }) => getReviews({ recipeId, mine: true, size: 1 }, signal),
    }),
};

export function useCreateReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ReviewCreateRequest) => createReview(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewQueries.all });
    },
  });
}

export function useUpdateReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reviewId, request }: { reviewId: number; request: ReviewUpdateRequest }) =>
      updateReview(reviewId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewQueries.all });
    },
  });
}

export function useDeleteReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reviewId: number) => deleteReview(reviewId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewQueries.all });
    },
  });
}

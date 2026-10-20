import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  createReview,
  updateReview,
  type ReviewCreateRequest,
  type ReviewUpdateRequest,
} from './api';

export const reviewQueries = {
  all: ['reviews'] as const,
  lists: () => [...reviewQueries.all, 'list'] as const,
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

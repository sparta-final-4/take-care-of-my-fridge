import {
  infiniteQueryOptions,
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';

import type { ReviewSearchRequestSortEnum } from '@/common/api/clients';

import {
  createReview,
  deleteReview,
  getReviews,
  updateReview,
  type ReviewCreateRequest,
  type ReviewUpdateRequest,
} from './api';

export const reviewQueries = {
  all: ['reviews'] as const,
  lists: () => [...reviewQueries.all, 'list'] as const,
  // 레시피 상세 후기 목록. 더 보기를 누르면 다음 페이지를 이어 받는다.
  infiniteList: (request: { recipeId: number; sort: ReviewSearchRequestSortEnum; size: number }) =>
    infiniteQueryOptions({
      queryKey: [...reviewQueries.lists(), request],
      queryFn: ({ pageParam, signal }) => getReviews({ ...request, page: pageParam }, signal),
      initialPageParam: 1,
      getNextPageParam: (lastPage) => {
        const isLast = lastPage.isLast ?? true;
        const pageNumber = lastPage.pageNumber ?? 1;
        return isLast ? undefined : pageNumber + 1;
      },
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

import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';

import type {
  CookingCompleteRequest,
  CookingLogSearchRequest,
  CookingLogUpdateRequest,
  MyRecipeSearchRequest,
} from '@/common/api/clients';
import { fridgeQueries } from '@/domains/fridge/queries';

import {
  completeCooking,
  getCookingLogs,
  getCookingStats,
  getMyRecipes,
  getPopularRecipes,
  updateCookingLog,
} from './api';

export const cookingQueries = {
  all: ['cooking'] as const,
  stats: (recipeId: number) =>
    queryOptions({
      queryKey: [...cookingQueries.all, 'stats', recipeId],
      queryFn: ({ signal }) => getCookingStats(recipeId, signal),
    }),
  logLists: () => [...cookingQueries.all, 'logs'] as const,
  logs: (request: CookingLogSearchRequest) =>
    queryOptions({
      queryKey: [...cookingQueries.logLists(), request],
      queryFn: ({ signal }) => getCookingLogs(request, signal),
    }),
  myRecipesList: () => [...cookingQueries.all, 'myRecipes'] as const,
  myRecipes: (request: MyRecipeSearchRequest = {}) =>
    queryOptions({
      queryKey: [...cookingQueries.myRecipesList(), request],
      queryFn: ({ signal }) => getMyRecipes(request, signal),
    }),
  popularList: () => [...cookingQueries.all, 'popular'] as const,
  popular: (size = 6) =>
    queryOptions({
      queryKey: [...cookingQueries.popularList(), size],
      queryFn: ({ signal }) => getPopularRecipes(size, signal),
      staleTime: 10 * 60 * 1000,
    }),
};

/**
 * 요리 기록 결과·메모 수정 Mutation
 * 성공 시 cookingQueries.all 전체를 무효화하여
 * 요리 기록 목록, 성공/실패 통계, 마이페이지 레시피 요약을 최신화한다.
 */
export function useUpdateCookingLog() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      cookingLogId,
      request,
    }: {
      cookingLogId: number;
      request: CookingLogUpdateRequest;
    }) => updateCookingLog(cookingLogId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cookingQueries.all });
    },
  });
}

/**
 * 요리 완료 생성 Mutation
 * 성공 시 fridgeQueries.all, cookingQueries.all을 무효화하여
 * 냉장고 재료 보유량, 요리 통계, 요리 기록, 후기 쓰기 가능 여부를 최신화한다.
 */
export function useCompleteCooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CookingCompleteRequest) => completeCooking(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: fridgeQueries.all });
      queryClient.invalidateQueries({ queryKey: cookingQueries.all });
    },
  });
}

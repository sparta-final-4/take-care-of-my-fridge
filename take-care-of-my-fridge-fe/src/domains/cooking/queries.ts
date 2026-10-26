import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';

import type {
  CookingLogSearchRequest,
  CookingLogUpdateRequest,
  MyRecipeSearchRequest,
} from '@/common/api/clients';

import { getCookingLogs, getCookingStats, getMyRecipes, updateCookingLog } from './api';

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

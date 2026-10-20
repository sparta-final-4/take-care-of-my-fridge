import { queryOptions, useQuery } from '@tanstack/react-query';

import type { CookingSortType, MyCookedRecipeItem } from './api';
import { MOCK_MY_COOKED_RECIPES } from './mocks';

export const cookingQueries = {
  all: ['cooking'] as const,
  myRecipesList: () => [...cookingQueries.all, 'myRecipes'] as const,
  myRecipes: (sort: CookingSortType) =>
    queryOptions({
      queryKey: [...cookingQueries.myRecipesList(), sort],
      // TODO: 백엔드에 GET /api/me/recipes 구현 시 getMyCookedRecipes(sort, signal)로 교체
      queryFn: async (): Promise<MyCookedRecipeItem[]> => {
        // 실제 API 준비 전까지는 mock 데이터 반환
        return Promise.resolve(MOCK_MY_COOKED_RECIPES);
      },
    }),
};

/**
 * 로그인한 회원의 요리 완료 레시피 목록 훅.
 * 컴포넌트는 api.ts 대신 queries.ts의 훅을 사용한다.
 */
export function useMyCookedRecipes(sort: CookingSortType = 'LATEST') {
  return useQuery(cookingQueries.myRecipes(sort));
}

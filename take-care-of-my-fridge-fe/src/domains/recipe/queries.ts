import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { RecipeListRequest } from '@/common/api/clients';

import { getRecipe, getRecipes } from './api';

export const recipeQueries = {
  all: ['recipes'] as const,
  list: (request: RecipeListRequest) =>
    queryOptions({
      queryKey: [...recipeQueries.all, 'list', request],
      queryFn: ({ signal }) => getRecipes(request, signal),
      // 조건·페이지를 바꿔도 이전 목록을 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
  detail: (recipeId: number) =>
    queryOptions({
      queryKey: [...recipeQueries.all, 'detail', recipeId],
      queryFn: ({ signal }) => getRecipe(recipeId, signal),
    }),
};

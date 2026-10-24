import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { RecipeListRequest } from '@/common/api/clients';

import { getRecipe, getRecipeClassifications, getRecipes } from './api';

export const recipeQueries = {
  all: ['recipes'] as const,
  // 분류는 레시피 등록, 수정 때만 바뀌므로 화면을 새로 열기 전까지 다시 받지 않는다.
  classifications: () =>
    queryOptions({
      queryKey: [...recipeQueries.all, 'classifications'],
      queryFn: ({ signal }) => getRecipeClassifications(signal),
      staleTime: Infinity,
    }),
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

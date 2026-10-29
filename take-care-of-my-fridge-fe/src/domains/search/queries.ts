import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { RecipeListRequest } from '@/common/api/clients';

import { searchRecipes } from './api';

export const searchQueries = {
  all: ['search'] as const,
  recipes: (request: RecipeListRequest) =>
    queryOptions({
      queryKey: [...searchQueries.all, 'recipes', request],
      queryFn: ({ signal }) => searchRecipes(request, signal),
      // 페이지를 바꿔도 이전 결과를 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
};

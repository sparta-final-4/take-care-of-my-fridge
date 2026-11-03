import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { ProductListRequest, RecipeListRequest } from '@/common/api/clients';

import { searchProducts, searchRecipes, suggestSearchKeywords } from './api';

export const searchQueries = {
  all: ['search'] as const,
  recipes: (request: RecipeListRequest) =>
    queryOptions({
      queryKey: [...searchQueries.all, 'recipes', request],
      queryFn: ({ signal }) => searchRecipes(request, signal),
      // 페이지를 바꿔도 이전 결과를 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
  products: (request: ProductListRequest) =>
    queryOptions({
      queryKey: [...searchQueries.all, 'products', request],
      queryFn: ({ signal }) => searchProducts(request, signal),
      placeholderData: keepPreviousData,
    }),
  // 같은 글자를 지웠다 다시 치면 캐시로 바로 보여 준다. 레시피 이름은 자주 바뀌지 않는다.
  suggestions: (keyword: string) =>
    queryOptions({
      queryKey: [...searchQueries.all, 'suggestions', keyword],
      queryFn: ({ signal }) => suggestSearchKeywords(keyword, signal),
      staleTime: 5 * 60 * 1000,
      placeholderData: keepPreviousData,
    }),
};

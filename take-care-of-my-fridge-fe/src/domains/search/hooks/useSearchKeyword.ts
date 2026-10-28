import { useSearchParams } from 'react-router';

import type { RecipeListRequest } from '@/common/api/clients';

export const SEARCH_RECIPE_PAGE_SIZE = 16;

// 상단 통합 검색의 검색어·페이지. 지금은 레시피만 찾으며, 상품·밀키트 결과는 이후 같은 검색어로 붙인다.
export function useSearchKeyword() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword')?.trim() ?? '';
  const pageParam = Number(searchParams.get('page'));
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam : 1;

  const setPage = (value: number) => {
    const next = new URLSearchParams(searchParams);
    if (value === 1) next.delete('page');
    else next.set('page', String(value));
    setSearchParams(next);
  };

  // 요약과 레시피 결과가 같은 요청을 써서 한 번만 조회한다.
  const recipeRequest: RecipeListRequest = {
    sort: 'LATEST',
    keyword,
    page,
    size: SEARCH_RECIPE_PAGE_SIZE,
  };

  return { keyword, page, setPage, recipeRequest };
}

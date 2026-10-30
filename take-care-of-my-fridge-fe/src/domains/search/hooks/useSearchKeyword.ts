import { useSearchParams } from 'react-router';

import type { ProductListRequest, RecipeListRequest } from '@/common/api/clients';

export const SEARCH_RECIPE_PAGE_SIZE = 8;
export const SEARCH_PRODUCT_PAGE_SIZE = 16;

// 요약의 개수 링크가 스크롤해 갈 결과 섹션
export const SEARCH_SECTION_IDS = {
  recipes: 'search-recipes',
  products: 'search-products',
} as const;

// 상단 통합 검색의 검색어와 결과 종류별 페이지. 레시피는 page, 상품은 productPage로 따로 넘긴다.
export function useSearchKeyword() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword')?.trim() ?? '';
  const page = readPage(searchParams.get('page'));
  const productPage = readPage(searchParams.get('productPage'));

  const updatePage = (key: 'page' | 'productPage', value: number) => {
    const next = new URLSearchParams(searchParams);
    if (value === 1) next.delete(key);
    else next.set(key, String(value));
    setSearchParams(next);
  };

  // 요약과 각 결과 섹션이 같은 요청을 써서 종류마다 한 번만 조회한다.
  const recipeRequest: RecipeListRequest = {
    sort: 'LATEST',
    keyword,
    page,
    size: SEARCH_RECIPE_PAGE_SIZE,
  };
  const productRequest: ProductListRequest = {
    sort: 'LATEST',
    keyword,
    page: productPage,
    size: SEARCH_PRODUCT_PAGE_SIZE,
  };

  return {
    keyword,
    page,
    productPage,
    setPage: (value: number) => updatePage('page', value),
    setProductPage: (value: number) => updatePage('productPage', value),
    recipeRequest,
    productRequest,
  };
}

function readPage(value: string | null) {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

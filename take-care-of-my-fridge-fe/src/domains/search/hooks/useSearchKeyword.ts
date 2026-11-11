import { useSearchParams } from 'react-router';

import type {
  ProductListRequest,
  ProductListRequestTypeEnum,
  RecipeListRequest,
} from '@/common/api/clients';
import { PRICE_RANGES, type PriceRangeKey } from '@/domains/product/format';
import { COOK_TIME_OPTIONS, type CookTimeKey } from '@/domains/recipe/format';

export const SEARCH_RECIPE_PAGE_SIZE = 8;
export const SEARCH_PRODUCT_PAGE_SIZE = 16;

// 요약의 개수 링크가 스크롤해 갈 결과 섹션
export const SEARCH_SECTION_IDS = {
  recipes: 'search-recipes',
  products: 'search-products',
} as const;

// 결과 종류마다 쓰는 URL 키. 레시피 조건을 바꾸면 레시피 페이지만, 상품 조건을 바꾸면 상품 페이지만 처음으로 돌린다.
const RECIPE_FILTER_KEYS = ['cuisine', 'time'] as const;
const PRODUCT_FILTER_KEYS = ['type', 'price'] as const;

// 상단 통합 검색의 검색어와 결과 종류별 필터·페이지. 레시피는 page, 상품은 productPage로 따로 넘긴다.
// 레시피·재료 목록 화면과 같은 URL 키(cuisine, time, type, price)를 쓴다.
export function useSearchKeyword() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword')?.trim() ?? '';
  const page = readPage(searchParams.get('page'));
  const productPage = readPage(searchParams.get('productPage'));
  // 음식 종류는 서버 목록에서 오므로 URL 값을 그대로 쓴다. 없는 값이면 결과가 비어 있을 뿐이다.
  const cuisineType = searchParams.get('cuisine') || undefined;
  const cookTime = COOK_TIME_OPTIONS.find((option) => option.key === searchParams.get('time'));
  const typeParam = searchParams.get('type');
  const type: ProductListRequestTypeEnum | undefined =
    typeParam === 'INGREDIENT' || typeParam === 'MEALKIT' ? typeParam : undefined;
  const priceRange = PRICE_RANGES.find((range) => range.key === searchParams.get('price'));

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setSearchParams(next);
  };

  const clear = (keys: readonly string[]) =>
    update(Object.fromEntries(keys.map((key) => [key, undefined])));

  // 요약과 각 결과 섹션이 같은 요청을 써서 종류마다 한 번만 조회한다.
  const recipeRequest: RecipeListRequest = {
    sort: 'RELEVANCE',
    keyword,
    // 통합 검색은 음식 종류를 하나만 고르고, API는 여러 값을 받으므로 한 개짜리 목록으로 보낸다.
    cuisineType: cuisineType ? [cuisineType] : undefined,
    maxCookTimeMin: cookTime?.maxCookTimeMin,
    page,
    size: SEARCH_RECIPE_PAGE_SIZE,
  };
  const productRequest: ProductListRequest = {
    sort: 'RELEVANCE',
    keyword,
    type,
    minPrice: priceRange && 'minPrice' in priceRange ? priceRange.minPrice : undefined,
    maxPrice: priceRange && 'maxPrice' in priceRange ? priceRange.maxPrice : undefined,
    page: productPage,
    size: SEARCH_PRODUCT_PAGE_SIZE,
  };

  return {
    keyword,
    page,
    productPage,
    cuisineType,
    cookTimeKey: cookTime?.key,
    type,
    priceRangeKey: priceRange?.key,
    recipeFiltered: Boolean(cuisineType || cookTime),
    productFiltered: Boolean(type || priceRange),
    setPage: (value: number) => update({ page: value === 1 ? undefined : String(value) }),
    setProductPage: (value: number) =>
      update({ productPage: value === 1 ? undefined : String(value) }),
    setCuisineType: (value: string | undefined) => update({ cuisine: value, page: undefined }),
    setCookTime: (key: CookTimeKey | undefined) => update({ time: key, page: undefined }),
    setType: (value: ProductListRequestTypeEnum | undefined) =>
      update({ type: value, productPage: undefined }),
    setPriceRange: (key: PriceRangeKey | undefined) =>
      update({ price: key, productPage: undefined }),
    resetRecipeFilters: () => clear([...RECIPE_FILTER_KEYS, 'page']),
    resetProductFilters: () => clear([...PRODUCT_FILTER_KEYS, 'productPage']),
    recipeRequest,
    productRequest,
  };
}

function readPage(value: string | null) {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

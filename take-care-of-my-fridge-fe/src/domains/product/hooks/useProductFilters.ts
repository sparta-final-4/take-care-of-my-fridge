import { useSearchParams } from 'react-router';

import type { ProductListRequest, ProductListRequestSortEnum } from '@/common/api/clients';
import { PRICE_RANGES, type PriceRangeKey } from '@/domains/product/format';

export const PRODUCT_SORT_OPTIONS: Array<{ value: ProductListRequestSortEnum; label: string }> = [
  { value: 'LATEST', label: '최신순' },
  { value: 'PRICE_ASC', label: '낮은 가격순' },
  { value: 'PRICE_DESC', label: '높은 가격순' },
];

// 검색어·가격대·정렬·페이지는 URL 쿼리에 두어 새로고침·뒤로 가기에도 유지한다.
// 기본값은 URL에서 지워 주소를 짧게 둔다.
export function useProductFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('q')?.trim() ?? '';
  const priceRange = PRICE_RANGES.find((range) => range.key === searchParams.get('price'));
  const sort =
    PRODUCT_SORT_OPTIONS.find((option) => option.value === searchParams.get('sort'))?.value ??
    'LATEST';
  const pageParam = Number(searchParams.get('page'));
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam : 1;

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setSearchParams(next);
  };

  // 조건이 바뀌면 결과가 달라지므로 첫 페이지로 돌아간다.
  const setKeyword = (value: string) => update({ q: value.trim() || undefined, page: undefined });
  const setPriceRange = (key: PriceRangeKey | undefined) => update({ price: key, page: undefined });
  const setSort = (value: ProductListRequestSortEnum) =>
    update({ sort: value === 'LATEST' ? undefined : value, page: undefined });
  const setPage = (value: number) => update({ page: value === 1 ? undefined : String(value) });
  const reset = () => setSearchParams({});

  const request: Omit<ProductListRequest, 'size'> = {
    sort,
    page,
    keyword: keyword || undefined,
    minPrice: priceRange && 'minPrice' in priceRange ? priceRange.minPrice : undefined,
    maxPrice: priceRange && 'maxPrice' in priceRange ? priceRange.maxPrice : undefined,
  };

  return {
    keyword,
    priceRangeKey: priceRange?.key,
    sort,
    page,
    request,
    filtered: Boolean(keyword || priceRange),
    setKeyword,
    setPriceRange,
    setSort,
    setPage,
    reset,
  };
}

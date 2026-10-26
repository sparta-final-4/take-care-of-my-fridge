import { useSearchParams } from 'react-router';

import type { RecipeListRequest, RecipeListRequestSortEnum } from '@/common/api/clients';
import { COOK_TIME_OPTIONS, type CookTimeKey } from '@/domains/recipe/format';

export const RECIPE_SORT_OPTIONS: Array<{ value: RecipeListRequestSortEnum; label: string }> = [
  { value: 'LATEST', label: '최신순' },
  { value: 'DIFFICULTY_ASC', label: '쉬운 순' },
  { value: 'NAME_ASC', label: '이름순' },
];

// 검색어·필터·정렬·페이지는 URL 쿼리에 두어 새로고침·뒤로 가기에도 유지한다.
// 기본값은 URL에서 지워 주소를 짧게 둔다.
export function useRecipeFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('q')?.trim() ?? '';
  // 분류 값은 서버 목록에서 오므로 URL 값을 그대로 쓴다. 없는 값이면 결과가 비어 있을 뿐이다.
  const cuisineType = searchParams.get('cuisine') || undefined;
  const category = searchParams.get('category') || undefined;
  const cookTime = COOK_TIME_OPTIONS.find((option) => option.key === searchParams.get('time'));
  const sort =
    RECIPE_SORT_OPTIONS.find((option) => option.value === searchParams.get('sort'))?.value ??
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
  const setCuisineType = (value: string | undefined) => update({ cuisine: value, page: undefined });
  const setCategory = (value: string | undefined) => update({ category: value, page: undefined });
  const setCookTime = (key: CookTimeKey | undefined) => update({ time: key, page: undefined });
  const setSort = (value: RecipeListRequestSortEnum) =>
    update({ sort: value === 'LATEST' ? undefined : value, page: undefined });
  const setPage = (value: number) => update({ page: value === 1 ? undefined : String(value) });
  const reset = () => setSearchParams({});

  const request: Omit<RecipeListRequest, 'size'> = {
    sort,
    page,
    keyword: keyword || undefined,
    cuisineType,
    category,
    maxCookTimeMin: cookTime?.maxCookTimeMin,
  };

  return {
    keyword,
    cuisineType,
    category,
    cookTimeKey: cookTime?.key,
    sort,
    page,
    request,
    filtered: Boolean(keyword || cuisineType || category || cookTime),
    setKeyword,
    setCuisineType,
    setCategory,
    setCookTime,
    setSort,
    setPage,
    reset,
  };
}

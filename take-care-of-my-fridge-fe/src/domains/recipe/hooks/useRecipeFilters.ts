import { useSearchParams } from 'react-router';

import type { RecipeListRequest, RecipeListRequestSortEnum } from '@/common/api/clients';
import { COOK_TIME_OPTIONS, type CookTimeKey } from '@/domains/recipe/format';

export const RECIPE_SORT_OPTIONS: Array<{ value: RecipeListRequestSortEnum; label: string }> = [
  { value: 'LATEST', label: '최신순' },
  { value: 'DIFFICULTY_ASC', label: '쉬운 순' },
  { value: 'NAME_ASC', label: '이름순' },
];

const RELEVANCE_SORT_OPTION: { value: RecipeListRequestSortEnum; label: string } = {
  value: 'RELEVANCE',
  label: '정확도순',
};

// 검색어·필터·정렬·페이지는 URL 쿼리에 두어 새로고침·뒤로 가기에도 유지한다.
// 기본값은 URL에서 지워 주소를 짧게 둔다.
export function useRecipeFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('q')?.trim() ?? '';
  // 분류 값은 서버 목록에서 오므로 URL 값을 그대로 쓴다. 없는 값이면 결과가 비어 있을 뿐이다.
  // 음식 종류·요리 분류는 여러 개 고를 수 있어 같은 이름을 반복해 둔다(?category=국&category=찌개).
  const cuisineTypes = uniqueValues(searchParams.getAll('cuisine'));
  const categories = uniqueValues(searchParams.getAll('category'));
  const cookTime = COOK_TIME_OPTIONS.find((option) => option.key === searchParams.get('time'));
  // 검색어가 있으면 Elasticsearch 점수로 줄 세우는 정확도순을 고를 수 있고 기본값도 정확도순이다.
  const sortOptions = keyword
    ? [RELEVANCE_SORT_OPTION, ...RECIPE_SORT_OPTIONS]
    : RECIPE_SORT_OPTIONS;
  const defaultSort: RecipeListRequestSortEnum = keyword ? 'RELEVANCE' : 'LATEST';
  const sort =
    sortOptions.find((option) => option.value === searchParams.get('sort'))?.value ?? defaultSort;
  const pageParam = Number(searchParams.get('page'));
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam : 1;

  const update = (changes: Record<string, string | string[] | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      next.delete(key);
      const values = Array.isArray(value) ? value : [value];
      values.forEach((item) => {
        if (item) next.append(key, item);
      });
    });
    setSearchParams(next);
  };

  // 조건이 바뀌면 결과가 달라지므로 첫 페이지로 돌아간다.
  const setKeyword = (value: string) => update({ q: value.trim() || undefined, page: undefined });
  const setCuisineTypes = (values: string[]) => update({ cuisine: values, page: undefined });
  const setCategories = (values: string[]) => update({ category: values, page: undefined });
  const setCookTime = (key: CookTimeKey | undefined) => update({ time: key, page: undefined });
  const setSort = (value: RecipeListRequestSortEnum) =>
    update({ sort: value === defaultSort ? undefined : value, page: undefined });
  const setPage = (value: number) => update({ page: value === 1 ? undefined : String(value) });
  const reset = () => setSearchParams({});

  const request: Omit<RecipeListRequest, 'size'> = {
    sort,
    page,
    keyword: keyword || undefined,
    cuisineType: cuisineTypes.length > 0 ? cuisineTypes : undefined,
    category: categories.length > 0 ? categories : undefined,
    maxCookTimeMin: cookTime?.maxCookTimeMin,
  };

  return {
    keyword,
    cuisineTypes,
    categories,
    cookTimeKey: cookTime?.key,
    sort,
    sortOptions,
    page,
    request,
    filtered: Boolean(keyword || cuisineTypes.length > 0 || categories.length > 0 || cookTime),
    setKeyword,
    setCuisineTypes,
    setCategories,
    setCookTime,
    setSort,
    setPage,
    reset,
  };
}

function uniqueValues(values: string[]) {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))];
}

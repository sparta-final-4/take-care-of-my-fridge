import { useSearchParams } from 'react-router';

import type { FridgeSortType } from '../api';

const SORT_TYPES: FridgeSortType[] = ['EXPIRATION', 'CREATED', 'NAME'];

function isSortType(value: string | null): value is FridgeSortType {
  return SORT_TYPES.some((sort) => sort === value);
}

// 정렬은 URL 쿼리(?sort=)에 둬서 같은 페이지의 섹션들이 같은 목록 쿼리를 공유하게 한다.
export function useFridgeSort() {
  const [searchParams, setSearchParams] = useSearchParams();
  const value = searchParams.get('sort');
  const sort: FridgeSortType = isSortType(value) ? value : 'EXPIRATION';

  const setSort = (next: FridgeSortType) => {
    setSearchParams(
      (params) => {
        params.set('sort', next);
        return params;
      },
      { replace: true },
    );
  };

  return [sort, setSort] as const;
}

import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { searchQueries } from '../queries';

// 글자를 칠 때마다 요청하지 않도록 입력이 멈추고 이만큼 지나야 조회한다.
const SUGGESTION_DEBOUNCE_MS = 250;

// 상단 검색창에 입력 중인 값으로 자동완성 후보를 찾는다. 비어 있으면 조회하지 않는다.
export function useSearchSuggestions(input: string) {
  const keyword = input.trim();
  const [debounced, setDebounced] = useState(keyword);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(keyword), SUGGESTION_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [keyword]);

  // 후보는 없어도 검색은 되므로 실패해도 따로 알리지 않는다.
  const { data } = useQuery({
    ...searchQueries.suggestions(debounced),
    enabled: debounced.length > 0,
  });

  return keyword && debounced ? (data ?? []) : [];
}

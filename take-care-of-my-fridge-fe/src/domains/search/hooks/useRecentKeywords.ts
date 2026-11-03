import { useSyncExternalStore } from 'react';

import { getRecentKeywords, subscribeRecentKeywords } from '../recentKeywords';

// 헤더와 모바일 메뉴의 검색창이 같은 목록을 보도록 저장소 변경을 구독한다.
export function useRecentKeywords() {
  return useSyncExternalStore(subscribeRecentKeywords, getRecentKeywords);
}

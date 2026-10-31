import type { FridgeItemResponse } from './api';
import { matchesFridgeTab, type FridgeTab } from './filter';

// 등록 직후 내 냉장고로 넘기는 라우터 state.
// 정렬·탭을 바꾸면 setSearchParams가 state 없이 이동해 강조가 사라진다(새로고침하면 브라우저가 기억한다).
export interface NewFridgeItemsState {
  newFridgeIds: number[];
}

type NewItem = Pick<FridgeItemResponse, 'basicSeasoning' | 'id' | 'ingredientType' | 'storage'>;

// 새 재료가 모두 기본 양념이면 전체 탭에 보이지 않으므로 기본 양념 탭으로 연다.
// 섞여 있으면 전체 탭으로 열고, 거기서 빠지는 기본 양념 수를 알려 준다.
export function getNewItemsView(items: NewItem[]) {
  const ids = items.flatMap((item) => (item.id === undefined ? [] : [item.id]));
  const seasoningCount = items.filter((item) => matchesFridgeTab(item, 'BASIC_SEASONING')).length;
  const onlySeasoning = items.length > 0 && seasoningCount === items.length;
  const tab: FridgeTab = onlySeasoning ? 'BASIC_SEASONING' : 'ALL';

  return { ids, tab, hiddenSeasoningCount: onlySeasoning ? 0 : seasoningCount };
}

export function readNewFridgeIds(state: unknown): number[] {
  if (typeof state !== 'object' || state === null || !('newFridgeIds' in state)) return [];
  const ids: unknown = state.newFridgeIds;
  if (!Array.isArray(ids)) return [];
  return ids.filter((id): id is number => typeof id === 'number');
}

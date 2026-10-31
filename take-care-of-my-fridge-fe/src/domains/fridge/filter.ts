import type { FridgeItemResponse, StorageType } from './api';

// 재료 목록 탭. 보관 방법 탭 뒤에 밀키트·기본 양념 모아보기 탭이 붙는다.
export type FridgeTab = 'ALL' | StorageType | 'MEALKIT' | 'BASIC_SEASONING';

export const FRIDGE_TABS: FridgeTab[] = [
  'ALL',
  'REFRIGERATED',
  'FROZEN',
  'ROOM_TEMP',
  'MEALKIT',
  'BASIC_SEASONING',
];

type FilterableItem = Pick<FridgeItemResponse, 'basicSeasoning' | 'ingredientType' | 'storage'>;

export function isFridgeTab(value: string | null): value is FridgeTab {
  return FRIDGE_TABS.some((tab) => tab === value);
}

// 기본 양념은 기본 양념 탭에서만 보이고(보관 방법 상관없음) 나머지 탭과 개수에서는 빠진다.
// 밀키트는 음식이라 임박 관리 대상이므로 전체·보관 방법 탭에도 그대로 들어간다.
export function matchesFridgeTab(item: FilterableItem, tab: FridgeTab) {
  if (tab === 'BASIC_SEASONING') return item.basicSeasoning === true;
  if (item.basicSeasoning === true) return false;
  if (tab === 'ALL') return true;
  if (tab === 'MEALKIT') return item.ingredientType === 'MEALKIT';
  return item.storage === tab;
}

// 요약·헤더·메인 냉장고의 재료 수는 기본 양념을 빼고 센다(밀키트는 포함).
export function withoutBasicSeasoning<T extends FilterableItem>(items: T[]) {
  return items.filter((item) => matchesFridgeTab(item, 'ALL'));
}

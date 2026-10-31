import { describe, expect, it } from 'vitest';

import { getNewItemsView, readNewFridgeIds } from './newItems';

const tofu = {
  id: 1,
  basicSeasoning: false,
  ingredientType: 'NORMAL',
  storage: 'REFRIGERATED',
} as const;
const soySauce = {
  id: 2,
  basicSeasoning: true,
  ingredientType: 'NORMAL',
  storage: 'REFRIGERATED',
} as const;
const salt = {
  id: 3,
  basicSeasoning: true,
  ingredientType: 'NORMAL',
  storage: 'ROOM_TEMP',
} as const;

describe('getNewItemsView', () => {
  it('일반 재료만 있으면 전체 탭으로 연다', () => {
    expect(getNewItemsView([tofu])).toEqual({ ids: [1], tab: 'ALL', hiddenSeasoningCount: 0 });
  });

  it('기본 양념만 있으면 기본 양념 탭으로 연다', () => {
    expect(getNewItemsView([soySauce, salt])).toEqual({
      ids: [2, 3],
      tab: 'BASIC_SEASONING',
      hiddenSeasoningCount: 0,
    });
  });

  it('섞여 있으면 전체 탭으로 열고 빠지는 기본 양념 수를 센다', () => {
    expect(getNewItemsView([tofu, soySauce, salt])).toEqual({
      ids: [1, 2, 3],
      tab: 'ALL',
      hiddenSeasoningCount: 2,
    });
  });

  it('id가 없는 항목은 강조 대상에서 뺀다', () => {
    expect(getNewItemsView([{ ...tofu, id: undefined }]).ids).toEqual([]);
  });
});

describe('readNewFridgeIds', () => {
  it('state의 숫자 id만 꺼낸다', () => {
    expect(readNewFridgeIds({ newFridgeIds: [1, '2', 3] })).toEqual([1, 3]);
  });

  it('state가 없거나 모양이 다르면 빈 목록이다', () => {
    expect(readNewFridgeIds(null)).toEqual([]);
    expect(readNewFridgeIds({ expandedRecipeId: 1 })).toEqual([]);
    expect(readNewFridgeIds({ newFridgeIds: 1 })).toEqual([]);
  });
});

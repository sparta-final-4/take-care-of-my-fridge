import { describe, expect, it } from 'vitest';

import { FRIDGE_TABS, matchesFridgeTab, withoutBasicSeasoning, type FridgeTab } from './filter';

const tofu = { basicSeasoning: false, ingredientType: 'NORMAL', storage: 'REFRIGERATED' } as const;
const frozenMealkit = {
  basicSeasoning: false,
  ingredientType: 'MEALKIT',
  storage: 'FROZEN',
} as const;
const soySauce = {
  basicSeasoning: true,
  ingredientType: 'NORMAL',
  storage: 'REFRIGERATED',
} as const;
// 생성 타입은 응답 필드가 선택이라 basicSeasoning이 없는 재료도 기본 양념이 아닌 것으로 본다.
const unknownSeasoning = { ingredientType: 'CUSTOM', storage: 'ROOM_TEMP' } as const;

function tabsOf(item: Parameters<typeof matchesFridgeTab>[0]): FridgeTab[] {
  return FRIDGE_TABS.filter((tab) => matchesFridgeTab(item, tab));
}

describe('matchesFridgeTab', () => {
  it('일반 재료는 전체와 자기 보관 방법 탭에만 보인다', () => {
    expect(tabsOf(tofu)).toEqual(['ALL', 'REFRIGERATED']);
  });

  it('밀키트는 전체·보관 방법 탭과 밀키트 탭에 보인다', () => {
    expect(tabsOf(frozenMealkit)).toEqual(['ALL', 'FROZEN', 'MEALKIT']);
  });

  it('기본 양념은 보관 방법과 상관없이 기본 양념 탭에만 보인다', () => {
    expect(tabsOf(soySauce)).toEqual(['BASIC_SEASONING']);
  });

  it('basicSeasoning이 없으면 기본 양념이 아닌 것으로 본다', () => {
    expect(tabsOf(unknownSeasoning)).toEqual(['ALL', 'ROOM_TEMP']);
  });
});

describe('withoutBasicSeasoning', () => {
  it('기본 양념만 빼고 밀키트는 남긴다', () => {
    expect(withoutBasicSeasoning([tofu, frozenMealkit, soySauce, unknownSeasoning])).toEqual([
      tofu,
      frozenMealkit,
      unknownSeasoning,
    ]);
  });
});

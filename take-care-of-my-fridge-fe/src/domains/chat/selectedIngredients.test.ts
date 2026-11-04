import { describe, expect, it } from 'vitest';

import type { FridgeItemResponse } from '@/common/api/clients';

import { selectedRecommendMessage, toChatSelectedIngredients } from './selectedIngredients';

function item(overrides: Partial<FridgeItemResponse>): FridgeItemResponse {
  return {
    ingredientId: 1,
    ingredientName: '배추',
    rawName: '배추',
    ingredientType: 'NORMAL',
    basicSeasoning: false,
    ...overrides,
  };
}

describe('toChatSelectedIngredients', () => {
  it('직접 입력·밀키트·기본 양념을 빼고 같은 재료는 한 번만 넣는다', () => {
    const selected = toChatSelectedIngredients([
      item({ id: 1, ingredientId: 10, ingredientName: '배추' }),
      item({ id: 2, ingredientId: 10, ingredientName: '배추', rawName: '알배추' }),
      item({ id: 3, ingredientId: 20, ingredientName: '버섯' }),
      item({ id: 4, ingredientId: 30, ingredientType: 'CUSTOM', ingredientName: '기타' }),
      item({ id: 5, ingredientId: 40, ingredientType: 'MEALKIT', ingredientName: '밀키트' }),
      item({ id: 6, ingredientId: 50, ingredientName: '소금', basicSeasoning: true }),
      item({ id: 7, ingredientId: undefined }),
    ]);

    expect(selected).toEqual([
      { ingredientId: 10, name: '배추' },
      { ingredientId: 20, name: '버섯' },
    ]);
  });

  it('넣을 재료가 없으면 빈 목록을 돌려준다', () => {
    expect(toChatSelectedIngredients([item({ ingredientType: 'CUSTOM' })])).toEqual([]);
  });
});

describe('selectedRecommendMessage', () => {
  it('선택 재료 이름을 모아 받침에 맞는 조사로 추천 문장을 만든다', () => {
    expect(
      selectedRecommendMessage([
        { ingredientId: 1, name: '배추' },
        { ingredientId: 2, name: '버섯' },
      ]),
    ).toBe('배추, 버섯으로 추천해줘');
    expect(selectedRecommendMessage([{ ingredientId: 1, name: '두부' }])).toBe('두부로 추천해줘');
    expect(selectedRecommendMessage([{ ingredientId: 1, name: '오이절임' }])).toBe(
      '오이절임으로 추천해줘',
    );
    expect(selectedRecommendMessage([{ ingredientId: 1, name: '파슬리' }])).toBe(
      '파슬리로 추천해줘',
    );
    expect(selectedRecommendMessage([{ ingredientId: 1, name: '쌀' }])).toBe('쌀로 추천해줘');
  });
});

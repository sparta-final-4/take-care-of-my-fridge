import { describe, expect, it } from 'vitest';

import { cardName, maskedCardNumber } from './cardFormat';

describe('cardName', () => {
  it('카드 상품명이 있으면 그대로 보인다', () => {
    expect(cardName({ cardName: '신한카드 Deep Dream' })).toBe('신한카드 Deep Dream');
  });

  it('카드 상품명이 없거나 비어 있으면 "등록 카드"로 보인다', () => {
    expect(cardName({})).toBe('등록 카드');
    expect(cardName({ cardName: '  ' })).toBe('등록 카드');
  });
});

describe('maskedCardNumber', () => {
  it('끝 4자리가 있으면 가린 번호로 보인다', () => {
    expect(maskedCardNumber({ cardLast4: '1234' })).toBe('···· 1234');
  });

  it('끝 4자리를 모르면 빈 문자열이다', () => {
    expect(maskedCardNumber({})).toBe('');
  });
});

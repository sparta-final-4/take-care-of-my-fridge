import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import { buildFridgeItemUpdateRequest, hasFridgeItemChanges } from './editRequest';

const item = {
  rawName: '찌개용 두부',
  quantity: 2,
  storage: 'REFRIGERATED' as const,
  expirationDate: '2026-11-03',
};

const unchanged = {
  rawName: '찌개용 두부',
  quantity: 2,
  storage: 'REFRIGERATED' as const,
  expirationDate: dayjs('2026-11-03'),
};

describe('buildFridgeItemUpdateRequest', () => {
  it('바뀐 것이 없으면 빈 요청이다', () => {
    expect(buildFridgeItemUpdateRequest(item, unchanged)).toEqual({});
  });

  it('소비기한만 바꾸면 소비기한만 담는다(수량은 보내지 않는다)', () => {
    expect(
      buildFridgeItemUpdateRequest(item, { ...unchanged, expirationDate: dayjs('2026-11-10') }),
    ).toEqual({ expirationDate: '2026-11-10' });
  });

  it('바뀐 칸을 모두 담는다', () => {
    expect(
      buildFridgeItemUpdateRequest(item, {
        rawName: '부침용 두부',
        quantity: 1.5,
        storage: 'FROZEN',
        expirationDate: dayjs('2026-12-01'),
      }),
    ).toEqual({
      rawName: '부침용 두부',
      quantity: 1.5,
      storage: 'FROZEN',
      expirationDate: '2026-12-01',
    });
  });

  it('이름은 앞뒤 공백을 빼고 비교한다', () => {
    expect(buildFridgeItemUpdateRequest(item, { ...unchanged, rawName: '  찌개용 두부 ' })).toEqual(
      {},
    );
  });

  it('비운 칸은 바꾸지 않은 것으로 본다', () => {
    expect(
      buildFridgeItemUpdateRequest(item, { ...unchanged, quantity: null, expirationDate: null }),
    ).toEqual({});
  });
});

describe('hasFridgeItemChanges', () => {
  it('그대로면 false다', () => {
    expect(hasFridgeItemChanges(item, unchanged)).toBe(false);
  });

  it('폼이 아직 값을 주지 않은 첫 렌더(빈 객체)는 false다', () => {
    expect(hasFridgeItemChanges(item, {})).toBe(false);
  });

  it('이름에 공백만 추가하면 false다', () => {
    expect(hasFridgeItemChanges(item, { ...unchanged, rawName: '  찌개용 두부 ' })).toBe(false);
  });

  it.each([
    ['이름', { rawName: '' }],
    ['수량', { quantity: null }],
    ['소비기한', { expirationDate: null }],
  ])('%s 칸을 비우면 true다', (_, cleared) => {
    expect(hasFridgeItemChanges(item, { ...unchanged, ...cleared })).toBe(true);
  });

  it('값을 바꾸면 true다', () => {
    expect(hasFridgeItemChanges(item, { ...unchanged, storage: 'FROZEN' })).toBe(true);
  });
});

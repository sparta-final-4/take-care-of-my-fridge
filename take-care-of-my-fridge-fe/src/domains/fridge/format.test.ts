import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  ddayTagColor,
  formatDDayLabel,
  formatLogDate,
  formatQuantity,
  getStep,
  withObjectParticle,
} from './format';

describe('getStep', () => {
  it.each(['개', '모', '마리', '캔', '접시', ' 개 '])('세는 단위 "%s"는 0.5', (unit) => {
    expect(getStep(unit)).toBe(0.5);
  });

  it.each(['g', 'kg', 'ml', 'ML', ' g '])('무게·부피 단위 "%s"는 10', (unit) => {
    expect(getStep(unit)).toBe(10);
  });

  it.each([null, undefined, '', '큰술', '컵'])('단위가 없거나 그 밖의 단위(%s)는 0.5', (unit) => {
    expect(getStep(unit)).toBe(0.5);
  });
});

describe('withObjectParticle', () => {
  it('받침이 있으면 을, 없으면 를을 붙인다', () => {
    expect(withObjectParticle('계란')).toBe('계란을');
    expect(withObjectParticle('두부')).toBe('두부를');
  });

  it('한글로 끝나지 않으면 을(를)을 붙인다', () => {
    expect(withObjectParticle('두부 300g')).toBe('두부 300g을(를)');
  });
});

describe('소비기한 태그', () => {
  it.each([
    [-2, '2일 지남', 'error'],
    [0, '오늘', 'error'],
    [1, 'D-1', 'error'],
    [3, 'D-3', 'warning'],
    [32, 'D-32', undefined],
  ])('dDay %i는 %s, 색 %s', (dDay, label, color) => {
    expect(formatDDayLabel(dDay)).toBe(label);
    expect(ddayTagColor(dDay)).toBe(color);
  });
});

describe('formatQuantity', () => {
  it('소수점 첫째 자리까지만 보여준다', () => {
    expect(formatQuantity({ quantity: 0.33, baseUnit: '개' })).toBe('0.3개');
    expect(formatQuantity({ quantity: 2, baseUnit: '개' })).toBe('2개');
    expect(formatQuantity({ quantity: 1.25 })).toBe('1.3');
  });

  it('반올림하면 0이 되는 작은 값은 원래 값을 보여준다', () => {
    expect(formatQuantity({ quantity: 0.04, baseUnit: '개' })).toBe('0.04개');
    expect(formatQuantity({ quantity: 0.01, baseUnit: '개' })).toBe('0.01개');
    expect(formatQuantity({ quantity: 0.05, baseUnit: '개' })).toBe('0.1개');
  });
});

describe('formatLogDate', () => {
  const now = dayjs('2026-10-29T15:00:00');

  it.each([
    ['2026-10-29T09:00:00', '오늘'],
    ['2026-10-28T23:59:00', '어제'],
    ['2026-10-27T10:00:00', '10.27'],
    ['2025-12-31T10:00:00', '25.12.31'],
  ])('%s는 %s', (value, expected) => {
    expect(formatLogDate(value, now)).toBe(expected);
  });
});

import dayjs from 'dayjs';
import 'dayjs/locale/ko';
import { beforeAll, describe, expect, it } from 'vitest';

import { formatCount, formatPeriod, formatPeriodShort, formatWon } from './format';

const week = { startDate: '2026-10-12', endDate: '2026-10-18' };

describe('정산 표기', () => {
  beforeAll(() => {
    dayjs.locale('ko');
  });

  it('금액과 건수에 천 단위 쉼표와 단위를 붙인다', () => {
    expect(formatWon(1234567)).toBe('1,234,567원');
    expect(formatWon(-5000)).toBe('-5,000원');
    expect(formatCount(undefined)).toBe('0건');
  });

  it('단위별로 기간을 표기한다', () => {
    expect(formatPeriod('DAY', { startDate: '2026-10-07', endDate: '2026-10-07' })).toBe(
      '2026.10.07 (수)',
    );
    expect(formatPeriod('WEEK', week)).toBe('2026.10.12 ~ 10.18');
    expect(formatPeriod('MONTH', { startDate: '2026-10-01', endDate: '2026-10-30' })).toBe(
      '2026.10',
    );
  });

  it('차트 축에는 짧은 기간을 쓴다', () => {
    expect(formatPeriodShort('DAY', { startDate: '2026-10-07', endDate: '2026-10-07' })).toBe(
      '10.07',
    );
    expect(formatPeriodShort('WEEK', week)).toBe('10.12~');
  });
});

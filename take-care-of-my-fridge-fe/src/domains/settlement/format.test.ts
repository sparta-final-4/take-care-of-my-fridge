import dayjs from 'dayjs';
import 'dayjs/locale/ko';
import { beforeAll, describe, expect, it } from 'vitest';

import type { PgProvider } from '@/domains/payment/pgProvider';

import {
  formatCount,
  formatPaymentMethod,
  formatPeriod,
  formatPeriodShort,
  formatWon,
} from './format';

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

describe('결제 수단 표기', () => {
  it('등록 카드 결제는 결제대행사와 관계없이 등록 카드로 보인다', () => {
    expect(formatPaymentMethod({ registeredCard: true, pgProvider: 'TOSSPAYMENTS' })).toBe(
      '등록 카드',
    );
  });

  it('결제창 결제는 결제대행사 이름으로 보인다', () => {
    expect(formatPaymentMethod({ registeredCard: false, pgProvider: 'KAKAOPAY' })).toBe(
      '카카오페이',
    );
    expect(formatPaymentMethod({ registeredCard: false, pgProvider: 'TOSSPAYMENTS' })).toBe(
      '토스페이먼츠',
    );
  });

  it('결제대행사가 없으면 빈칸(-)이다', () => {
    expect(formatPaymentMethod({})).toBe('-');
  });

  it('이름을 모르는 결제대행사는 코드값을 그대로 보인다', () => {
    expect(formatPaymentMethod({ pgProvider: 'NEWPG' as PgProvider })).toBe('NEWPG');
  });
});

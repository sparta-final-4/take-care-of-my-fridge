import { describe, expect, it } from 'vitest';

import { averagePaidAmount, refundRate } from './metrics';

describe('정산 비율 지표', () => {
  it('환불률은 환불금액을 결제금액으로 나눈 백분율이다', () => {
    expect(refundRate({ paidAmount: 200_000, refundedAmount: 10_000 })).toBe(5);
    expect(refundRate({ paidAmount: 10_000, refundedAmount: 15_000 })).toBe(150);
    expect(refundRate({ paidAmount: 10_000 })).toBe(0);
  });

  it('결제가 없으면 환불률을 정하지 않는다', () => {
    expect(refundRate({ paidAmount: 0, refundedAmount: 5_000 })).toBeNull();
    expect(refundRate({})).toBeNull();
  });

  it('객단가는 결제금액을 결제 건수로 나눠 원 단위로 반올림한다', () => {
    expect(averagePaidAmount({ paidAmount: 100_000, paidCount: 3 })).toBe(33_333);
    expect(averagePaidAmount({ paidAmount: 50_000, paidCount: 0 })).toBeNull();
  });
});

import type { SalesAmountResponse } from './api';

type Sales = Pick<SalesAmountResponse, 'paidAmount' | 'refundedAmount' | 'paidCount'>;

// 환불률(%) = 환불금액 ÷ 결제금액. 환불은 환불한 날의 매출이라 예전 결제를 환불하면 100%를 넘을 수 있다.
// 결제가 없는 기간은 비율을 정할 수 없어 null
export function refundRate(sales: Sales): number | null {
  const paid = sales.paidAmount ?? 0;
  if (paid <= 0) return null;
  return ((sales.refundedAmount ?? 0) / paid) * 100;
}

// 객단가(원) = 결제금액 ÷ 결제 건수, 원 단위 반올림. 결제가 없는 기간은 null
export function averagePaidAmount(sales: Sales): number | null {
  const count = sales.paidCount ?? 0;
  if (count <= 0) return null;
  return Math.round((sales.paidAmount ?? 0) / count);
}

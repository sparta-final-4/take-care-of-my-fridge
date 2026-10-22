import type { OrderSummaryResponse } from '@/common/api/clients';

export const ORDER_STATUS_LABELS: Record<NonNullable<OrderSummaryResponse['status']>, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  CANCELLED: '취소',
  CONFIRMED: '구매 확정',
};

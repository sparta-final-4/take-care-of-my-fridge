import type { OrderSummaryResponse } from '@/common/api/clients';

export const ORDER_STATUS_LABELS: Record<NonNullable<OrderSummaryResponse['status']>, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  CANCELLED: '취소',
  CONFIRMED: '구매 확정',
  REFUNDED: '전액 환불',
};

// 상태 태그 색. 결제 대기·취소는 기본색이다.
// antd 기본 색 이름만 쓴다. processing 같은 상태 색은 테마 정보 색(잉크)을 따라가 어둡게 칠해진다.
export const ORDER_STATUS_COLORS: Partial<
  Record<NonNullable<OrderSummaryResponse['status']>, string>
> = {
  PAID: 'geekblue',
  CONFIRMED: 'green',
  REFUNDED: 'purple',
};

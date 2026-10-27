import type {
  RefundableItemResponse,
  RefundCreateRequest,
  RefundHistoryResponse,
} from '@/common/api/clients';

type RefundReasonType = RefundCreateRequest['reasonType'];
type RefundStatus = NonNullable<RefundHistoryResponse['status']>;

// 환불 내역에 짧게 보여 주는 사유
export const REFUND_REASON_LABELS: Record<RefundReasonType, string> = {
  CHANGE_OF_MIND: '단순 변심',
  ORDER_MISTAKE: '주문 실수',
  NO_LONGER_NEEDED: '더 이상 필요 없음',
  ETC: '기타',
};

// 환불 신청 화면의 사유 선택지
export const REFUND_REASON_OPTIONS: { value: RefundReasonType; label: string }[] = [
  { value: 'CHANGE_OF_MIND', label: '단순 변심' },
  { value: 'ORDER_MISTAKE', label: '주문 실수 (다른 상품·수량을 잘못 주문)' },
  { value: 'NO_LONGER_NEEDED', label: '더 이상 필요하지 않아요' },
  { value: 'ETC', label: '기타' },
];

export const REFUND_STATUS_LABELS: Record<RefundStatus, string> = {
  REQUESTED: '환불 진행 중',
  COMPLETED: '환불 완료',
  FAILED: '환불 실패',
};

export type RefundRowState = 'refundable' | 'refunded' | 'saleBlocked';

// 특가·번개 드롭은 남은 수량과 상관없이 환불할 수 없다. 나머지는 남은 수량이 없으면 모두 환불한 상품이다.
export function getRefundRowState(item: RefundableItemResponse): RefundRowState {
  if (item.saleType === 'SPECIAL' || item.saleType === 'FLASH_DROP') return 'saleBlocked';
  return (item.refundableQuantity ?? 0) > 0 ? 'refundable' : 'refunded';
}

export type OrderItemRefundState = 'none' | 'partial' | 'full' | 'saleBlocked';

// 주문 상세 상품 행의 환불 표시. 진행 중인 환불 수량도 환불한 수량으로 센다.
export function getOrderItemRefundState(item: RefundableItemResponse): OrderItemRefundState {
  if (item.saleType === 'SPECIAL' || item.saleType === 'FLASH_DROP') return 'saleBlocked';
  const refunded = item.refundedQuantity ?? 0;
  if (refunded === 0) return 'none';
  return refunded < (item.orderedQuantity ?? 0) ? 'partial' : 'full';
}

// 진행 중인 환불도 서버가 이미 환불한 수량으로 세므로 금액에 들어간다.
export function sumRefundedAmount(items: RefundableItemResponse[]) {
  return items.reduce((sum, item) => sum + (item.refundedQuantity ?? 0) * (item.unitPrice ?? 0), 0);
}

export function sumRefundAmountByStatus(history: RefundHistoryResponse[], status: RefundStatus) {
  return history
    .filter((refund) => refund.status === status)
    .reduce((sum, refund) => sum + (refund.refundAmount ?? 0), 0);
}

export interface RefundedItem {
  orderItemId: number;
  productName: string;
  productImageUrl?: string;
  unitPrice: number;
  quantity: number;
  amount: number;
}

// 실패한 환불을 뺀 환불 상품을 주문 상품별로 합친다. 처음 환불한 순서대로 둔다.
export function groupRefundedItems(history: RefundHistoryResponse[]): RefundedItem[] {
  const grouped = new Map<number, RefundedItem>();
  [...history]
    .reverse()
    .filter((refund) => refund.status !== 'FAILED')
    .flatMap((refund) => refund.items ?? [])
    .forEach((item) => {
      const orderItemId = item.orderItemId ?? 0;
      const current = grouped.get(orderItemId);
      grouped.set(orderItemId, {
        orderItemId,
        productName: item.productName ?? '',
        productImageUrl: item.productImageUrl,
        unitPrice: item.unitPrice ?? 0,
        quantity: (current?.quantity ?? 0) + (item.quantity ?? 0),
        amount: (current?.amount ?? 0) + (item.amount ?? 0),
      });
    });
  return [...grouped.values()];
}

// 주문 전체의 환불 상태. 진행 중인 환불이 하나라도 있으면 진행 중, 완료가 있으면 완료로 본다.
export function getOverallRefundStatus(history: RefundHistoryResponse[]): RefundStatus | undefined {
  if (history.some((refund) => refund.status === 'REQUESTED')) return 'REQUESTED';
  if (history.some((refund) => refund.status === 'COMPLETED')) return 'COMPLETED';
  return history.length > 0 ? 'FAILED' : undefined;
}

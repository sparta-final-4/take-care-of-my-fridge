import {
  refundApi,
  type RefundableItemResponse,
  type RefundCreateRequest,
  type RefundHistoryResponse,
  type RefundResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// 특가·번개 드롭 상품은 refundable=false, refundableQuantity=0으로 온다.
// 결제 완료가 아닌 주문은 서버가 오류로 거절하므로 결제 완료 주문에서만 부른다.
export async function getRefundableItems(
  orderId: number,
  signal?: AbortSignal,
): Promise<RefundableItemResponse[]> {
  return unwrap(refundApi.getRefundableItems({ orderId }, { signal }));
}

// 최신 환불이 먼저 온다. 완료 전 환불은 refundedAt이 없다.
export async function getRefundHistory(
  orderId: number,
  signal?: AbortSignal,
): Promise<RefundHistoryResponse[]> {
  return unwrap(refundApi.getRefundHistory({ orderId }, { signal }));
}

// 서버가 PG 취소까지 끝낸 뒤 응답한다. PG 결과를 확정하지 못하면 REQUESTED로 오고 서버가 나중에 마무리한다.
export async function requestRefund(request: RefundCreateRequest): Promise<RefundResponse> {
  return unwrap(refundApi.request({ refundCreateRequest: request }));
}

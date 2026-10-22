import {
  orderApi,
  type OrderCancelResponse,
  type OrderCheckoutResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// 같은 멱등 키로 다시 보내면 서버는 처음 만든 주문을 돌려준다.
export async function createOrder(
  cartItemIds: number[],
  idempotencyKey: string,
): Promise<OrderCheckoutResponse> {
  return unwrap(orderApi.createOrder({ orderCreateRequest: { cartItemIds }, idempotencyKey }));
}

export async function getOrderCheckout(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderCheckoutResponse> {
  return unwrap(orderApi.getCheckout({ orderId }, { signal }));
}

// 이미 취소된 주문에도 서버는 같은 성공 응답을 준다. 재고는 처음 취소할 때만 돌아간다.
export async function cancelOrder(orderId: number): Promise<OrderCancelResponse> {
  return unwrap(orderApi.cancelOrder({ orderId }));
}

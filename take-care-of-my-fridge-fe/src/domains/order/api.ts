import {
  orderApi,
  type OrderCheckoutResponse,
  type OrderListRequest,
  type PageResponseOrderSummaryResponse,
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

export async function getMyOrders(
  request: OrderListRequest,
  signal?: AbortSignal,
): Promise<PageResponseOrderSummaryResponse> {
  return unwrap(orderApi.getMyOrders({ request }, { signal }));
}

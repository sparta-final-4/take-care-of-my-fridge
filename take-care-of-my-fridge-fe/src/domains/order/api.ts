import {
  orderApi,
  type OrderCancelResponse,
  type OrderCheckoutResponse,
  type OrderConfirmResponse,
  type OrderCreateItemRequest,
  type OrderDetailResponse,
  type OrderListRequest,
  type PageResponseOrderSummaryResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// 같은 멱등 키로 다시 보내면 서버는 처음 만든 주문을 돌려준다.
// items에는 사용자가 장바구니에서 본 단가와 할인 구분을 담는다. 주문 시점 값과 다르면 서버가 409로 거절한다.
export async function createOrder(
  items: OrderCreateItemRequest[],
  idempotencyKey: string,
): Promise<OrderCheckoutResponse> {
  return unwrap(orderApi.createOrder({ orderCreateRequest: { items }, idempotencyKey }));
}

export async function getOrderCheckout(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderCheckoutResponse> {
  return unwrap(orderApi.getCheckout({ orderId }, { signal }));
}

export async function getOrderDetail(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderDetailResponse> {
  return unwrap(orderApi.getDetail({ orderId }, { signal }));
}

// 이미 취소된 주문에도 서버는 같은 성공 응답을 준다. 재고는 처음 취소할 때만 돌아간다.
export async function cancelOrder(orderId: number): Promise<OrderCancelResponse> {
  return unwrap(orderApi.cancelOrder({ orderId }));
}

// 이미 구매 확정된 주문에도 서버는 같은 성공 응답을 준다.
export async function confirmOrder(orderId: number): Promise<OrderConfirmResponse> {
  return unwrap(orderApi.confirmOrder({ orderId }));
}

export async function getMyOrders(
  request: OrderListRequest,
  signal?: AbortSignal,
): Promise<PageResponseOrderSummaryResponse> {
  return unwrap(orderApi.getMyOrders({ request }, { signal }));
}

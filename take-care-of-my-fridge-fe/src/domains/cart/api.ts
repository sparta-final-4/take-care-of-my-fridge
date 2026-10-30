import { axiosInstance } from '@/common/api/axiosInstance';
import { cartApi, type AddCartItemRequest, type AddCartItemResponse } from '@/common/api/clients';
import type { ApiResponse } from '@/common/api/types';
import { unwrap } from '@/common/api/unwrap';

// CartItemResponse.java와 필드·enum을 맞춘다. 할인 중이 아니면 saleType·discountRate는 null이다.
export interface CartItemResponse {
  cartItemId: number;
  productId: number;
  productName: string;
  imageUrl: string | null;
  quantity: number;
  listPrice: number;
  unitPrice: number;
  saleType: 'SPECIAL' | 'FLASH_DROP' | null;
  discountRate: number | null;
  flashDropRemaining: number | null;
  // 번개 드롭 한정 수량(총 판매 수량)
  flashDropLimit: number | null;
  // 진행 중인 할인의 종료 시각. 남은 시간은 이 값으로 화면이 계산한다.
  saleEndsAt: string | null;
  soldOut: boolean;
}

export interface CartItemsResponse {
  items: CartItemResponse[];
}

export interface ChangeCartItemQuantityRequest {
  quantity: number;
}

export interface ChangeCartItemQuantityResponse {
  cartItemId: number;
  quantity: number;
}

export async function getCartItems(signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<CartItemsResponse>>('/api/cart-items', {
    signal,
  });
  return data.data.items;
}

// 이미 담긴 상품이면 백엔드가 수량을 더한다.
export async function addCartItem(request: AddCartItemRequest): Promise<AddCartItemResponse> {
  return unwrap(cartApi.addItem({ addCartItemRequest: request }));
}

export async function changeCartItemQuantity(
  cartItemId: number,
  request: ChangeCartItemQuantityRequest,
) {
  const { data } = await axiosInstance.patch<ApiResponse<ChangeCartItemQuantityResponse>>(
    `/api/cart-items/${encodeURIComponent(cartItemId)}`,
    request,
  );
  return data.data;
}

// 백엔드는 cartItemIds=1,2 형식을 받는다. axios 기본 배열 직렬화(cartItemIds[]=1)는 받지 못한다.
export async function removeCartItems(cartItemIds: number[]) {
  await axiosInstance.delete('/api/cart-items', {
    params: { cartItemIds: cartItemIds.join(',') },
  });
}

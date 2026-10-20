import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';
import { csrfHeaders } from '@/domains/auth/api';

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

export async function changeCartItemQuantity(
  cartItemId: number,
  request: ChangeCartItemQuantityRequest,
) {
  const { data } = await axiosInstance.patch<ApiResponse<ChangeCartItemQuantityResponse>>(
    `/api/cart-items/${encodeURIComponent(cartItemId)}`,
    request,
    { headers: await csrfHeaders() },
  );
  return data.data;
}

// 백엔드는 cartItemIds=1,2 형식을 받는다. axios 기본 배열 직렬화(cartItemIds[]=1)는 받지 못한다.
export async function removeCartItems(cartItemIds: number[]) {
  await axiosInstance.delete('/api/cart-items', {
    params: { cartItemIds: cartItemIds.join(',') },
    headers: await csrfHeaders(),
  });
}

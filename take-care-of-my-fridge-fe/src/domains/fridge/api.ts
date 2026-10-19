import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';
import { csrfHeaders } from '@/domains/auth/api';

export type StorageType = 'REFRIGERATED' | 'FROZEN' | 'ROOM_TEMP';

export type FridgeSortType = 'EXPIRATION' | 'CREATED' | 'NAME';

// FridgeItemResponse.java와 필드·enum을 맞춘다. 숫자(BigDecimal)는 number로 직렬화된다.
export interface FridgeItemResponse {
  id: number;
  ingredientId: number | null;
  ingredientName: string | null;
  baseUnit: string | null;
  rawName: string;
  quantity: number;
  storage: StorageType;
  expirationDate: string;
  source: 'MANUAL' | 'COMMERCE';
  orderItemId: number | null;
  imminent: boolean;
  expired: boolean;
  createdAt: string;
  updatedAt: string;
}

// FridgeItemCreateRequest.java. storage·expirationDate를 비우면 서버가 재료 기준값으로 채운다.
export interface FridgeItemCreateRequest {
  items: Array<{
    rawName: string;
    quantity: number;
    storage?: StorageType;
    expirationDate?: string;
  }>;
}

export async function getFridgeItems(sort: FridgeSortType, signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<FridgeItemResponse[]>>('/api/fridge/items', {
    params: { sort },
    signal,
  });
  return data.data;
}

export async function createFridgeItems(request: FridgeItemCreateRequest) {
  const { data } = await axiosInstance.post<ApiResponse<FridgeItemResponse[]>>(
    '/api/fridge/items',
    request,
    { headers: await csrfHeaders() },
  );
  return data.data;
}

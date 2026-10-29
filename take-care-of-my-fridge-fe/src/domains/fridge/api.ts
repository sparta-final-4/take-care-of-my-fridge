import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse, PageResponse } from '@/common/api/types';

export type StorageType = 'REFRIGERATED' | 'FROZEN' | 'ROOM_TEMP';

export type FridgeSortType = 'EXPIRATION' | 'CREATED' | 'NAME';

// FridgeItemStatus.java
export type FridgeItemStatus = 'EXPIRED' | 'IMMINENT' | 'NORMAL';

// FridgeRemoveReason.java
export type FridgeRemoveReason = 'CONSUMED' | 'DISCARDED' | 'WRONG_ENTRY';

// FridgeItemAction.java
export type FridgeItemAction = 'CONSUMED' | 'DISCARDED';

// IngredientType.java
export type IngredientType = 'NORMAL' | 'CUSTOM' | 'MEALKIT';

// FridgeItemResponse.java와 필드·enum을 맞춘다. 숫자(BigDecimal)는 number로 직렬화된다.
export interface FridgeItemResponse {
  id: number;
  ingredientId: number | null;
  ingredientName: string | null;
  ingredientType: IngredientType;
  baseUnit: string | null;
  rawName: string;
  quantity: number;
  storage: StorageType;
  expirationDate: string;
  source: 'MANUAL' | 'COMMERCE';
  orderItemId: number | null;
  imminent: boolean;
  expired: boolean;
  // 서버 기준 소비기한까지 남은 일수 (오늘 0, 지나면 음수)
  dDay: number;
  status: FridgeItemStatus;
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

// FridgeItemUpdateRequest.java. 모든 필드가 필수다.
export interface FridgeItemUpdateRequest {
  rawName: string;
  quantity: number;
  storage: StorageType;
  expirationDate: string;
}

// FridgeItemTakeOutRequest.java. quantity가 null이면 남은 양 전부를 뺀다.
export interface FridgeItemTakeOutRequest {
  action: FridgeItemAction;
  quantity: number | null;
}

// FridgeItemTakeOutResponse.java
export interface FridgeItemTakeOutResponse {
  fridgeId: number;
  removed: boolean;
  remainingQuantity: number;
}

// FridgeItemLogSearchRequest.java. 비운 조건은 빠진다. page는 1부터, size 기본 20·최대 100.
export interface FridgeItemLogSearchRequest {
  action?: FridgeItemAction;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

// FridgeItemLogResponse.java
export interface FridgeItemLogResponse {
  id: number;
  ingredientId: number | null;
  ingredientName: string | null;
  baseUnit: string | null;
  rawName: string;
  // 냉장고에서 실제로 뺀 양
  quantity: number;
  action: FridgeItemAction;
  cookedByRecipe: boolean;
  createdAt: string;
}

export async function getFridgeItemLogs(params: FridgeItemLogSearchRequest, signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<PageResponse<FridgeItemLogResponse>>>(
    '/api/fridge/logs',
    { params, signal },
  );
  return data.data;
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
  );
  return data.data;
}

export async function updateFridgeItem(fridgeId: number, request: FridgeItemUpdateRequest) {
  const { data } = await axiosInstance.patch<ApiResponse<FridgeItemResponse>>(
    `/api/fridge/items/${encodeURIComponent(fridgeId)}`,
    request,
  );
  return data.data;
}

export async function takeOutFridgeItem(fridgeId: number, request: FridgeItemTakeOutRequest) {
  const { data } = await axiosInstance.post<ApiResponse<FridgeItemTakeOutResponse>>(
    `/api/fridge/items/${encodeURIComponent(fridgeId)}/take-out`,
    request,
  );
  return data.data;
}

export async function removeFridgeItem(fridgeId: number, reason: FridgeRemoveReason) {
  await axiosInstance.delete(`/api/fridge/items/${encodeURIComponent(fridgeId)}`, {
    params: { reason },
  });
}

import {
  fridgeApi,
  type FridgeItemCreateRequest,
  type FridgeItemLogResponse,
  type FridgeItemLogSearchRequest,
  type FridgeItemResponse,
  type FridgeItemResponseIngredientTypeEnum,
  type FridgeItemResponseStatusEnum,
  type FridgeItemResponseStorageEnum,
  type FridgeItemSearchRequestSortEnum,
  type FridgeItemTakeOutRequest,
  type FridgeItemTakeOutRequestActionEnum,
  type FridgeItemTakeOutResponse,
  type FridgeItemUpdateRequest,
  type PageResponseFridgeItemLogResponse,
  type RemoveItemReasonEnum,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// 생성 enum은 모델마다 따로 나오지만 값이 같아 화면에서 쓰는 이름으로 하나씩만 둔다.
export type StorageType = FridgeItemResponseStorageEnum;
export type FridgeSortType = FridgeItemSearchRequestSortEnum;
export type FridgeItemStatus = FridgeItemResponseStatusEnum;
export type FridgeRemoveReason = RemoveItemReasonEnum;
export type FridgeItemAction = FridgeItemTakeOutRequestActionEnum;
export type IngredientType = FridgeItemResponseIngredientTypeEnum;

// storage·expirationDate를 비우면 서버가 재료 기준값으로 채운다.
// 수정은 바뀐 칸만 보내는 부분 수정이라 모든 필드가 선택이다(보내지 않은 칸은 그대로).
// 꺼내기에서 quantity를 빼면 남은 양 전부를 뺀다.
// dDay는 서버 기준 소비기한까지 남은 일수다(오늘 0, 지나면 음수).
export type {
  FridgeItemCreateRequest,
  FridgeItemLogResponse,
  FridgeItemLogSearchRequest,
  FridgeItemResponse,
  FridgeItemTakeOutRequest,
  FridgeItemTakeOutResponse,
  FridgeItemUpdateRequest,
  PageResponseFridgeItemLogResponse,
};

// page는 1부터, size 기본 20·최대 100. 비운 조건은 빠진다.
export async function getFridgeItemLogs(
  request: FridgeItemLogSearchRequest,
  signal?: AbortSignal,
): Promise<PageResponseFridgeItemLogResponse> {
  return unwrap(fridgeApi.getLogs({ request }, { signal }));
}

export async function getFridgeItems(
  sort: FridgeSortType,
  signal?: AbortSignal,
): Promise<FridgeItemResponse[]> {
  return unwrap(fridgeApi.getItems({ request: { sort } }, { signal }));
}

export async function createFridgeItems(
  request: FridgeItemCreateRequest,
): Promise<FridgeItemResponse[]> {
  return unwrap(fridgeApi.createItems({ fridgeItemCreateRequest: request }));
}

export async function updateFridgeItem(
  fridgeId: number,
  request: FridgeItemUpdateRequest,
): Promise<FridgeItemResponse> {
  return unwrap(fridgeApi.updateItem({ fridgeId, fridgeItemUpdateRequest: request }));
}

export async function takeOutFridgeItem(
  fridgeId: number,
  request: FridgeItemTakeOutRequest,
): Promise<FridgeItemTakeOutResponse> {
  return unwrap(fridgeApi.takeOutItem({ fridgeId, fridgeItemTakeOutRequest: request }));
}

export async function removeFridgeItem(fridgeId: number, reason: FridgeRemoveReason) {
  await fridgeApi.removeItem({ fridgeId, reason });
}

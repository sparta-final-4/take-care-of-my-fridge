import type { Dayjs } from 'dayjs';

import type { FridgeItemResponse, FridgeItemUpdateRequest, StorageType } from './api';

// 수정 팝업의 폼 값. 입력칸을 비우면 antd가 null을 준다.
export interface EditFormValues {
  rawName: string;
  quantity: number | null;
  storage: StorageType;
  expirationDate: Dayjs | null;
}

// 팝업을 열 때 값(item)과 비교해 바뀐 칸만 담는다.
// 수정 창을 열어 둔 사이 요리 등으로 바뀐 수량을 옛 값으로 덮어쓰지 않기 위해서다.
// 이름은 앞뒤 공백을 빼고 비교하고, 비운 칸은 바꾸지 않은 것으로 본다(검증은 폼 rules가 한다).
export function buildFridgeItemUpdateRequest(
  item: Pick<FridgeItemResponse, 'rawName' | 'quantity' | 'storage' | 'expirationDate'>,
  values: Partial<EditFormValues>,
): FridgeItemUpdateRequest {
  const request: FridgeItemUpdateRequest = {};

  const rawName = values.rawName?.trim();
  if (rawName && rawName !== item.rawName) request.rawName = rawName;

  if (typeof values.quantity === 'number' && values.quantity !== item.quantity) {
    request.quantity = values.quantity;
  }

  if (values.storage && values.storage !== item.storage) request.storage = values.storage;

  const expirationDate = values.expirationDate?.format('YYYY-MM-DD');
  if (expirationDate && expirationDate !== item.expirationDate) {
    request.expirationDate = expirationDate;
  }

  return request;
}

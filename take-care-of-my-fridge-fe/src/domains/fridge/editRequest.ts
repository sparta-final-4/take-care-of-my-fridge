import type { Dayjs } from 'dayjs';

import type { FridgeItemResponse, FridgeItemUpdateRequest, StorageType } from './api';

// 수정 팝업의 폼 값. 입력칸을 비우면 antd가 null을 준다.
export interface EditFormValues {
  rawName: string;
  quantity: number | null;
  storage: StorageType;
  expirationDate: Dayjs | null;
}

type EditableItem = Pick<FridgeItemResponse, 'rawName' | 'quantity' | 'storage' | 'expirationDate'>;

// 저장 버튼을 켤지 정한다. 처음 값과 다른 칸이 하나라도 있으면 true이고, 비운 칸도 바뀐 것으로 본다.
// 비운 칸이 있으면 버튼은 켜지고, 누르면 폼 rules 안내로 막힌다.
// undefined는 폼이 아직 값을 주지 않은 첫 렌더라서 바뀌지 않은 것으로 본다.
export function hasFridgeItemChanges(item: EditableItem, values: Partial<EditFormValues>) {
  const { rawName, quantity, storage, expirationDate } = values;
  return (
    (rawName !== undefined && rawName.trim() !== item.rawName) ||
    (quantity !== undefined && quantity !== item.quantity) ||
    (storage !== undefined && storage !== item.storage) ||
    (expirationDate !== undefined && expirationDate?.format('YYYY-MM-DD') !== item.expirationDate)
  );
}

// 팝업을 열 때 값(item)과 비교해 바뀐 칸만 담는다.
// 수정 창을 열어 둔 사이 요리 등으로 바뀐 수량을 옛 값으로 덮어쓰지 않기 위해서다.
// 이름은 앞뒤 공백을 빼고 비교하고, 비운 칸은 담지 않는다(저장 시점에는 폼 rules를 통과한 값만 온다).
export function buildFridgeItemUpdateRequest(
  item: EditableItem,
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

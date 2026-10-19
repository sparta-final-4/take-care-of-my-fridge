import type { FridgeItemResponse, StorageType } from './api';

export const STORAGE_LABELS: Record<StorageType, string> = {
  REFRIGERATED: '냉장',
  FROZEN: '냉동',
  ROOM_TEMP: '상온',
};

// 서버가 계산한 dDay를 표시 문구로만 바꾼다.
export function formatDDay(dDay: number) {
  if (dDay === 0) return 'D-Day';
  return dDay > 0 ? `D-${dDay}` : `D+${-dDay}`;
}

// 재료명 뒤 목적격 조사. 마지막 글자에 받침이 있으면 '을', 없으면 '를', 한글이 아니면 '을(를)'.
export function withObjectParticle(name: string) {
  const code = name.charCodeAt(name.length - 1) - 0xac00;
  if (!(code >= 0 && code <= 11171)) return `${name}을(를)`;
  return `${name}${code % 28 === 0 ? '를' : '을'}`;
}

export function formatQuantity(item: Pick<FridgeItemResponse, 'quantity' | 'baseUnit'>) {
  return `${item.quantity}${item.baseUnit ?? ''}`;
}

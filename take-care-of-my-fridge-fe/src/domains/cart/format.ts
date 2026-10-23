import type { CartItemResponse } from './api';

export function formatPrice(price: number) {
  return `${price.toLocaleString('ko-KR')}원`;
}

const pad = (value: number) => String(value).padStart(2, '0');

// 남은 시간을 "HH:MM:SS"로, 하루가 넘으면 "N일 HH:MM:SS"로 쓴다.
export function formatRemaining(milliseconds: number) {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const time = `${pad(hours)}:${pad(minutes)}:${pad(totalSeconds % 60)}`;
  return days > 0 ? `${days}일 ${time}` : time;
}

export const SALE_TYPE_LABELS: Record<NonNullable<CartItemResponse['saleType']>, string> = {
  SPECIAL: '특가',
  FLASH_DROP: '번개 드롭',
};

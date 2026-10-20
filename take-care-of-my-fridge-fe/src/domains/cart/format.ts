import type { CartItemResponse } from './api';

export function formatPrice(price: number) {
  return `${price.toLocaleString('ko-KR')}원`;
}

export const SALE_TYPE_LABELS: Record<NonNullable<CartItemResponse['saleType']>, string> = {
  SPECIAL: '특가',
  FLASH_DROP: '번개 드롭',
};

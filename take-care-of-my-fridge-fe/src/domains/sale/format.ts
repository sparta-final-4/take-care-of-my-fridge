// 서버와 같은 계산이다. 정가에서 할인율만큼 빼고 원 아래는 버린다.
export const salePriceOf = (price: number, discountRate: number) =>
  Math.floor((price * (100 - discountRate)) / 100);

export const MIN_DISCOUNT_RATE = 1;
export const MAX_DISCOUNT_RATE = 99;

export const SALE_STATUS_LABELS = {
  SCHEDULED: '예정',
  ACTIVE: '진행 중',
  ENDED: '종료',
} as const;

export const SALE_TYPE_LABELS = {
  SPECIAL: '특가',
  FLASH_DROP: '번개 드롭',
} as const;

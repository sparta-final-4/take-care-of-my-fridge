import type { Dayjs } from 'dayjs';

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

// 번개 드롭은 1인 1개다.
export const FLASH_DROP_PURCHASE_LIMIT = 1;

// 할인 행사 목록, 상품, 장바구니, 주문 화면이 같은 색으로 할인 구분을 보여 준다.
export const SALE_TYPE_COLORS = { SPECIAL: 'geekblue', FLASH_DROP: 'volcano' } as const;

export const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';
// 서버는 시각을 한국 시간으로 비교하므로 시간대 없는 형식으로 보낸다.
export const REQUEST_TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

const range = (count: number) => Array.from({ length: count }, (_, index) => index);

// minimum 이전의 날짜는 막는다.
export const disabledDateBefore = (minimum: Dayjs) => (date: Dayjs) =>
  date.isBefore(minimum, 'day');

// minimum과 같은 날에는 그 시각 이전(같은 분 포함)의 시·분을 막는다.
export const disabledTimeBefore = (minimum: Dayjs) => (date: Dayjs | null) => {
  if (!date?.isSame(minimum, 'day')) return {};
  return {
    disabledHours: () => range(minimum.hour()),
    disabledMinutes: (hour: number) => (hour === minimum.hour() ? range(minimum.minute() + 1) : []),
  };
};

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

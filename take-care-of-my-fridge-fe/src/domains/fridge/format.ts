import dayjs, { type Dayjs } from 'dayjs';

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

// 냉장고 목록의 소비기한 태그 문구. 지난 재료는 "N일 지남", 오늘은 "오늘"로 풀어 쓴다.
export function formatDDayLabel(dDay: number) {
  if (dDay < 0) return `${-dDay}일 지남`;
  if (dDay === 0) return '오늘';
  return `D-${dDay}`;
}

// 지남·오늘·D-1은 빨강, D-2~3은 주황, D-4부터는 색 없음(배경 없이 회색 글자)
export function ddayTagColor(dDay: number) {
  if (dDay <= 1) return 'error';
  if (dDay <= 3) return 'warning';
  return undefined;
}

// 재료명 뒤 목적격 조사. 마지막 글자에 받침이 있으면 '을', 없으면 '를', 한글이 아니면 '을(를)'.
export function withObjectParticle(name: string) {
  const code = name.charCodeAt(name.length - 1) - 0xac00;
  if (!(code >= 0 && code <= 11171)) return `${name}을(를)`;
  return `${name}${code % 28 === 0 ? '를' : '을'}`;
}

// 수량은 소수점 첫째 자리까지만 보여준다 (0.33개 → 0.3개, 2.00개 → 2개).
// 0.04처럼 반올림하면 0이 되는 작은 값은 0개로 보이지 않도록 원래 값을 그대로 쓴다.
export function formatQuantity(item: Pick<FridgeItemResponse, 'quantity' | 'baseUnit'>) {
  const rounded = Math.round(item.quantity * 10) / 10;
  const display = rounded === 0 && item.quantity > 0 ? item.quantity : rounded;
  return `${display}${item.baseUnit ?? ''}`;
}

// 최근 기록 날짜. 오늘·어제는 말로, 그 외는 MM.DD, 올해가 아니면 YY.MM.DD
export function formatLogDate(value: string, now: Dayjs = dayjs()) {
  const date = dayjs(value);
  if (date.isSame(now, 'day')) return '오늘';
  if (date.isSame(now.subtract(1, 'day'), 'day')) return '어제';
  return date.format(date.isSame(now, 'year') ? 'MM.DD' : 'YY.MM.DD');
}

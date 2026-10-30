import dayjs from 'dayjs';

import type { SettlementPeriodResponse, SettlementUnit } from './api';

export const formatWon = (value?: number) => `${(value ?? 0).toLocaleString('ko-KR')}원`;
export const formatCount = (value?: number) => `${(value ?? 0).toLocaleString('ko-KR')}건`;

// 표·툴팁에 보이는 기간. 조회 기간에서 잘린 주는 잘린 날짜 그대로 보여 준다
export function formatPeriod(unit: SettlementUnit, period: SettlementPeriodResponse) {
  const start = dayjs(period.startDate);
  const end = dayjs(period.endDate);
  if (unit === 'DAY') return start.format('YYYY.MM.DD (dd)');
  if (unit === 'MONTH') return start.format('YYYY.MM');
  return `${start.format('YYYY.MM.DD')} ~ ${end.format('MM.DD')}`;
}

// 차트 X축에 들어가는 짧은 기간
export function formatPeriodShort(unit: SettlementUnit, period: SettlementPeriodResponse) {
  const start = dayjs(period.startDate);
  if (unit === 'MONTH') return start.format('YYYY.MM');
  if (unit === 'WEEK') return `${start.format('MM.DD')}~`;
  return start.format('MM.DD');
}

import dayjs from 'dayjs';

import { PG_PROVIDER_OPTIONS, type PgProvider } from '@/domains/payment/pgProvider';

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

// 결제 수단 이름. 등록 카드 결제는 결제대행사와 관계없이 "등록 카드"로 보인다.
// 이름을 모르는 결제대행사(백엔드에 새로 생긴 값)는 코드값을 그대로 보여 준다
export function formatPaymentMethod(method: { registeredCard?: boolean; pgProvider?: PgProvider }) {
  if (method.registeredCard) return '등록 카드';
  const label = PG_PROVIDER_OPTIONS.find((option) => option.value === method.pgProvider)?.label;
  return label ?? method.pgProvider ?? '-';
}

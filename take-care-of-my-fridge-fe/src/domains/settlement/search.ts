import dayjs, { type Dayjs } from 'dayjs';

import type { SettlementSearch, SettlementUnit } from './api';

export const SETTLEMENT_DATE_FORMAT = 'YYYY-MM-DD';
// 백엔드 SettlementPeriodRule과 같은 규칙: 종료일 < 시작일 + 3개월
const MAX_PERIOD_MONTHS = 3;
// 백엔드 SettlementPaymentSearchRequest의 page 상한과 같다
const MAX_PAGE = 10_000;

export const SETTLEMENT_UNIT_OPTIONS: { label: string; value: SettlementUnit }[] = [
  { label: '일', value: 'DAY' },
  { label: '주', value: 'WEEK' },
  { label: '월', value: 'MONTH' },
];

export type SettlementTab = 'summary' | 'payments' | 'daily';

export const SETTLEMENT_TABS: { key: SettlementTab; label: string }[] = [
  { key: 'summary', label: '요약' },
  { key: 'payments', label: '건별' },
  { key: 'daily', label: '일자별' },
];

// URL에서 읽는 정산 화면 조건. page는 건별 탭에서만 쓴다
export interface SettlementView extends SettlementSearch {
  tab: SettlementTab;
  page: number;
}

function isWithinMaxPeriod(start: Dayjs, end: Dayjs) {
  return end.isBefore(start.add(MAX_PERIOD_MONTHS, 'month'), 'day');
}

// 오늘 이후와, 먼저 고른 날에서 3개월 이상 떨어진 날은 고를 수 없다
export function disabledSettlementDate(current: Dayjs, { from }: { from?: Dayjs }) {
  if (current.isAfter(dayjs(), 'day')) return true;
  if (!from) return false;
  return current.isBefore(from, 'day')
    ? !isWithinMaxPeriod(current, from)
    : !isWithinMaxPeriod(from, current);
}

function parseDate(value: string | null) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const date = dayjs(value);
  return date.isValid() && date.format(SETTLEMENT_DATE_FORMAT) === value ? date : null;
}

// URL 조건이 없거나 규칙에 맞지 않으면 요약 탭, 이번 달 1일 ~ 오늘, 일 단위, 1페이지로 본다
export function readSettlementView(searchParams: URLSearchParams): SettlementView {
  const today = dayjs();
  const tabParam = searchParams.get('tab');
  const tab = SETTLEMENT_TABS.find((item) => item.key === tabParam)?.key ?? 'summary';
  const unitParam = searchParams.get('unit');
  const unit = SETTLEMENT_UNIT_OPTIONS.find((option) => option.value === unitParam)?.value ?? 'DAY';
  const start = parseDate(searchParams.get('startDate'));
  const end = parseDate(searchParams.get('endDate'));
  const validRange =
    start &&
    end &&
    !end.isBefore(start, 'day') &&
    !end.isAfter(today, 'day') &&
    isWithinMaxPeriod(start, end);
  const pageParam = Number(searchParams.get('page'));
  return {
    tab,
    unit,
    startDate: (validRange ? start : today.startOf('month')).format(SETTLEMENT_DATE_FORMAT),
    endDate: (validRange ? end : today).format(SETTLEMENT_DATE_FORMAT),
    page: Number.isInteger(pageParam) && pageParam >= 1 && pageParam <= MAX_PAGE ? pageParam : 1,
  };
}

export function toSettlementParams(view: SettlementView) {
  return {
    tab: view.tab,
    unit: view.unit,
    startDate: view.startDate,
    endDate: view.endDate,
    page: String(view.page),
  };
}

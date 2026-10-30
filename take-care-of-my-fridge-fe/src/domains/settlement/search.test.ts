import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import { readSettlementView, toSettlementParams } from './search';

const today = dayjs().format('YYYY-MM-DD');
const monthStart = dayjs().startOf('month').format('YYYY-MM-DD');

describe('정산 화면 URL 조건', () => {
  it('조건이 없으면 요약 탭, 이번 달 1일 ~ 오늘, 일 단위, 1페이지다', () => {
    expect(readSettlementView(new URLSearchParams())).toEqual({
      tab: 'summary',
      unit: 'DAY',
      startDate: monthStart,
      endDate: today,
      page: 1,
    });
  });

  it('올바른 조건은 그대로 읽는다', () => {
    const start = dayjs().subtract(1, 'month').format('YYYY-MM-DD');
    const view = readSettlementView(
      new URLSearchParams({
        tab: 'payments',
        unit: 'WEEK',
        startDate: start,
        endDate: today,
        page: '3',
      }),
    );

    expect(view).toEqual({
      tab: 'payments',
      unit: 'WEEK',
      startDate: start,
      endDate: today,
      page: 3,
    });
    expect(new URLSearchParams(toSettlementParams(view)).get('page')).toBe('3');
  });

  it('규칙에 맞지 않는 탭·단위·기간·페이지는 기본값으로 바꾼다', () => {
    const tomorrow = dayjs().add(1, 'day').format('YYYY-MM-DD');
    const view = readSettlementView(
      new URLSearchParams({
        tab: 'chart',
        unit: 'YEAR',
        startDate: today,
        endDate: tomorrow,
        page: '0',
      }),
    );

    expect(view).toEqual({
      tab: 'summary',
      unit: 'DAY',
      startDate: monthStart,
      endDate: today,
      page: 1,
    });
  });

  it('3개월 이상 떨어진 기간은 기본 기간으로 바꾼다', () => {
    const start = dayjs().subtract(3, 'month').format('YYYY-MM-DD');
    const view = readSettlementView(new URLSearchParams({ startDate: start, endDate: today }));

    expect(view.startDate).toBe(monthStart);
  });
});

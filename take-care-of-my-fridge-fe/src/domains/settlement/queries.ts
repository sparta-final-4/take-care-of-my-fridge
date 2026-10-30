import { queryOptions } from '@tanstack/react-query';

import { getSettlement, getTodaySales, type SettlementSearch } from './api';

export const settlementQueries = {
  all: ['settlement'] as const,
  list: (search: SettlementSearch) =>
    queryOptions({
      queryKey: [...settlementQueries.all, 'list', search.unit, search.startDate, search.endDate],
      queryFn: ({ signal }) => getSettlement(search, signal),
      // 기간을 바꾸는 동안 이전 결과를 유지한다. 단위가 바뀌면 행 기간 표기가 달라서 유지하지 않는다
      placeholderData: (previous, previousQuery) =>
        previousQuery?.queryKey[2] === search.unit ? previous : undefined,
    }),
  today: () =>
    queryOptions({
      queryKey: [...settlementQueries.all, 'today'],
      queryFn: ({ signal }) => getTodaySales(signal),
    }),
};

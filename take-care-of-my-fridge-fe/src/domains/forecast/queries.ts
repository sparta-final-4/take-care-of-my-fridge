import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import { getErrorCode } from '@/common/api/errors';

import { getForecast } from './api';

export const forecastQueries = {
  all: ['forecast'] as const,
  detail: (weekStart?: string, demo = false) =>
    queryOptions({
      queryKey: [...forecastQueries.all, demo ? 'demo' : (weekStart ?? 'latest')],
      queryFn: ({ signal }) => getForecast(weekStart, signal, demo),
      // 예측 없음(F001)은 다시 요청해도 같아서 재시도하지 않는다.
      retry: (failureCount, error) => getErrorCode(error) !== 'F001' && failureCount < 1,
      // 실제 주차끼리 이동할 때만 이전 결과를 잠시 유지한다.
      placeholderData: (previousData, previousQuery) =>
        demo || previousQuery?.queryKey[1] === 'demo' ? undefined : keepPreviousData(previousData),
    }),
};

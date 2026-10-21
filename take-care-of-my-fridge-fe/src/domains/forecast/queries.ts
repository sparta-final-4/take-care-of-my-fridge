import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import { getErrorCode } from '@/common/api/errors';

import { getForecast } from './api';

export const forecastQueries = {
  all: ['forecast'] as const,
  detail: (weekStart?: string) =>
    queryOptions({
      queryKey: [...forecastQueries.all, weekStart ?? 'latest'],
      queryFn: ({ signal }) => getForecast(weekStart, signal),
      // 예측 없음(F001)은 다시 요청해도 같아서 재시도하지 않는다.
      retry: (failureCount, error) => getErrorCode(error) !== 'F001' && failureCount < 1,
      // 주를 바꾸는 동안 이전 주 화면을 유지한다.
      placeholderData: keepPreviousData,
    }),
};

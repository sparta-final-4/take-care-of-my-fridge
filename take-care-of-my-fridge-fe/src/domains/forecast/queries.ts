import { queryOptions } from '@tanstack/react-query';

import { getForecast } from './api';

export const forecastQueries = {
  detail: (weekStart?: string) =>
    queryOptions({
      queryKey: ['forecast', weekStart ?? 'latest'],
      queryFn: ({ signal }) => getForecast(weekStart, signal),
    }),
};

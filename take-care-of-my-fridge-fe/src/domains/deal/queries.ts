import { queryOptions } from '@tanstack/react-query';

import { getLatestDealAnalysis } from './api';

export const dealQueries = {
  all: ['dealAnalysis'] as const,
  latest: () =>
    queryOptions({
      queryKey: [...dealQueries.all, 'latest'],
      queryFn: ({ signal }) => getLatestDealAnalysis(signal),
    }),
};

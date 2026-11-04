import { queryOptions } from '@tanstack/react-query';

import { getCards } from './api';

export const billingKeyQueries = {
  all: ['billing-keys'] as const,
  list: () =>
    queryOptions({
      queryKey: [...billingKeyQueries.all, 'list'],
      queryFn: ({ signal }) => getCards(signal),
    }),
};

import { queryOptions } from '@tanstack/react-query';

import { getCards, getPinStatus } from './api';

export const billingKeyQueries = {
  all: ['billing-keys'] as const,
  list: () =>
    queryOptions({
      queryKey: [...billingKeyQueries.all, 'list'],
      queryFn: ({ signal }) => getCards(signal),
    }),
};

export const paymentPinQueries = {
  all: ['payment-pin'] as const,
  status: () =>
    queryOptions({
      queryKey: [...paymentPinQueries.all, 'status'],
      queryFn: ({ signal }) => getPinStatus(signal),
    }),
};

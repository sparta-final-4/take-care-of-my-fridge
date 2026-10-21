import { queryOptions } from '@tanstack/react-query';

import { getOrderCheckout } from './api';

export const orderQueries = {
  all: ['orders'] as const,
  checkout: (orderId: number) =>
    queryOptions({
      queryKey: [...orderQueries.all, 'checkout', orderId],
      queryFn: ({ signal }) => getOrderCheckout(orderId, signal),
    }),
};

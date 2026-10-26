import { queryOptions } from '@tanstack/react-query';

import { getRefundableItems, getRefundHistory } from './api';

export const refundQueries = {
  all: ['refunds'] as const,
  refundableItems: (orderId: number) =>
    queryOptions({
      queryKey: [...refundQueries.all, 'refundable-items', orderId],
      queryFn: ({ signal }) => getRefundableItems(orderId, signal),
    }),
  history: (orderId: number) =>
    queryOptions({
      queryKey: [...refundQueries.all, 'history', orderId],
      queryFn: ({ signal }) => getRefundHistory(orderId, signal),
    }),
};

import { queryOptions } from '@tanstack/react-query';

import { getCartItems } from './api';

export const cartQueries = {
  all: ['cart'] as const,
  items: () =>
    queryOptions({
      queryKey: [...cartQueries.all, 'items'],
      queryFn: ({ signal }) => getCartItems(signal),
    }),
};

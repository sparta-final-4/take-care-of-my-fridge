import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { OrderListRequest } from '@/common/api/clients';

import { getMyOrders, getOrderCheckout } from './api';

export const orderQueries = {
  all: ['orders'] as const,
  list: (request: OrderListRequest) =>
    queryOptions({
      queryKey: [...orderQueries.all, 'list', request],
      queryFn: ({ signal }) => getMyOrders(request, signal),
      // 페이지를 바꿔도 이전 목록을 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
  checkout: (orderId: number) =>
    queryOptions({
      queryKey: [...orderQueries.all, 'checkout', orderId],
      queryFn: ({ signal }) => getOrderCheckout(orderId, signal),
    }),
};

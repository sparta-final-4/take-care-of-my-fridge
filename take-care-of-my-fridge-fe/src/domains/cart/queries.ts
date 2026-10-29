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

// 장바구니 수량 변경·삭제. 진행 중인지 확인할 때(주문 버튼 등) 쓴다.
export const cartChangeMutationKey = [...cartQueries.all, 'change'] as const;

import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { ProductListRequest } from '@/common/api/clients';

import { getProduct, getProducts } from './api';

export const productQueries = {
  all: ['products'] as const,
  list: (request: ProductListRequest) =>
    queryOptions({
      queryKey: [...productQueries.all, 'list', request],
      queryFn: ({ signal }) => getProducts(request, signal),
      // 정렬·페이지를 바꿔도 이전 목록을 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
  detail: (productId: number) =>
    queryOptions({
      queryKey: [...productQueries.all, 'detail', productId],
      queryFn: ({ signal }) => getProduct(productId, signal),
    }),
};

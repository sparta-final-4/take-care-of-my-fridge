import { keepPreviousData, queryOptions } from '@tanstack/react-query';

import type { SaleCandidateProductsRequest } from '@/common/api/clients';

import { getSaleCandidateProducts } from './api';

export const saleQueries = {
  all: ['sale'] as const,
  // 행사를 등록하면 목록에서 빠지는 상품이 생겨 등록 뒤 all을 무효화한다.
  candidates: (request: SaleCandidateProductsRequest) =>
    queryOptions({
      queryKey: [...saleQueries.all, 'candidates', request],
      queryFn: ({ signal }) => getSaleCandidateProducts(request, signal),
      // 검색·페이지를 바꿔도 이전 목록을 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
};

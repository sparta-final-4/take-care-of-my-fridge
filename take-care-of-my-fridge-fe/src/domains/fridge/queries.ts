import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { createFridgeItems, getFridgeItems, type FridgeSortType } from './api';

export const fridgeQueries = {
  all: ['fridge'] as const,
  itemLists: () => [...fridgeQueries.all, 'items'] as const,
  items: (sort: FridgeSortType) =>
    queryOptions({
      queryKey: [...fridgeQueries.itemLists(), sort],
      queryFn: ({ signal }) => getFridgeItems(sort, signal),
    }),
};

// 화면은 api.ts 대신 아래 훅만 쓴다. 생성 클라이언트를 도입해도 api.ts만 바꾸면 된다.
export function useFridgeItems(sort: FridgeSortType, enabled: boolean) {
  return useQuery({ ...fridgeQueries.items(sort), enabled });
}

export function useCreateFridgeItems() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createFridgeItems,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: fridgeQueries.itemLists() }),
  });
}

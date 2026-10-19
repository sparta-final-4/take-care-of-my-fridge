import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import {
  createFridgeItems,
  getFridgeItemLogs,
  getFridgeItems,
  removeFridgeItem,
  takeOutFridgeItem,
  updateFridgeItem,
  type FridgeItemLogSearchRequest,
  type FridgeItemTakeOutRequest,
  type FridgeItemUpdateRequest,
  type FridgeRemoveReason,
  type FridgeSortType,
} from './api';

export const fridgeQueries = {
  all: ['fridge'] as const,
  itemLists: () => [...fridgeQueries.all, 'items'] as const,
  items: (sort: FridgeSortType) =>
    queryOptions({
      queryKey: [...fridgeQueries.itemLists(), sort],
      queryFn: ({ signal }) => getFridgeItems(sort, signal),
    }),
  logLists: () => [...fridgeQueries.all, 'logs'] as const,
  logs: (params: FridgeItemLogSearchRequest) =>
    queryOptions({
      queryKey: [...fridgeQueries.logLists(), params],
      queryFn: ({ signal }) => getFridgeItemLogs(params, signal),
    }),
};

// 화면은 api.ts 대신 아래 훅만 쓴다. 생성 클라이언트를 도입해도 api.ts만 바꾸면 된다.
export function useFridgeItems(sort: FridgeSortType, enabled: boolean) {
  // 정렬을 바꿔도 이전 목록을 보여 줘 화면이 비지 않게 한다.
  return useQuery({ ...fridgeQueries.items(sort), enabled, placeholderData: keepPreviousData });
}

export function useFridgeLogs(params: FridgeItemLogSearchRequest) {
  return useQuery(fridgeQueries.logs(params));
}

export function useCreateFridgeItems() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createFridgeItems,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: fridgeQueries.itemLists() }),
  });
}

export function useUpdateFridgeItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ fridgeId, request }: { fridgeId: number; request: FridgeItemUpdateRequest }) =>
      updateFridgeItem(fridgeId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: fridgeQueries.itemLists() }),
  });
}

export function useTakeOutFridgeItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ fridgeId, request }: { fridgeId: number; request: FridgeItemTakeOutRequest }) =>
      takeOutFridgeItem(fridgeId, request),
    // 최근 기록과 이번 달 버린 재료 수는 모두 logLists 아래 쿼리다.
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: fridgeQueries.itemLists() }),
        queryClient.invalidateQueries({ queryKey: fridgeQueries.logLists() }),
      ]),
  });
}

export function useRemoveFridgeItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ fridgeId, reason }: { fridgeId: number; reason: FridgeRemoveReason }) =>
      removeFridgeItem(fridgeId, reason),
    // 화면에서는 기록 없이 지우는 WRONG_ENTRY에만 쓰므로 기록 쿼리는 무효화하지 않는다.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: fridgeQueries.itemLists() }),
  });
}

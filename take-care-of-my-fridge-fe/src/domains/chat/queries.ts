import {
  infiniteQueryOptions,
  keepPreviousData,
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';

import type { ChatSessionListRequestStatusEnum } from '@/common/api/clients';

import { completeSession, getMessages, getMySessions, startSession } from './api';

export const chatQueries = {
  all: () => ['chat'] as const,
  activeSession: () => ['chat', 'activeSession'] as const,
  messages: (sessionId: number) => ['chat', 'messages', sessionId] as const,
  sessions: () => ['chat', 'sessions'] as const,

  activeSessionQuery: () =>
    queryOptions({
      queryKey: chatQueries.activeSession(),
      queryFn: ({ signal }) => startSession(signal),
      staleTime: Infinity,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      retry: false,
    }),

  messagesQuery: (sessionId: number) =>
    queryOptions({
      queryKey: chatQueries.messages(sessionId),
      queryFn: ({ signal }) => getMessages(sessionId, signal),
      enabled: sessionId > 0,
    }),

  // 상담 내역. 목록 끝까지 내리면 다음 페이지를 이어 받는다. 탭 건수는 첫 페이지의 counts를 쓴다.
  sessionListInfiniteQuery: (request: { status: ChatSessionListRequestStatusEnum; size: number }) =>
    infiniteQueryOptions({
      queryKey: [...chatQueries.sessions(), request],
      queryFn: ({ pageParam, signal }) => getMySessions({ ...request, page: pageParam }, signal),
      initialPageParam: 1,
      getNextPageParam: (lastPage) => {
        const isLast = lastPage.sessions?.isLast ?? true;
        const pageNumber = lastPage.sessions?.pageNumber ?? 1;
        return isLast ? undefined : pageNumber + 1;
      },
      // 탭을 바꿔도 이전 목록·건수를 보여 줘 화면이 비지 않게 한다.
      placeholderData: keepPreviousData,
    }),
};

/**
 * 활성 상담 세션을 시작(또는 이어받기)하는 mutation
 */
export function useStartChatSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => startSession(),
    onSuccess: (session) => {
      queryClient.setQueryData(chatQueries.activeSession(), session);
      // 새 상담을 시작하며 이전 상담이 만료 처리될 수 있어 상담 내역도 다시 받는다.
      queryClient.invalidateQueries({ queryKey: chatQueries.sessions() });
    },
  });
}

/**
 * 상담 세션을 종료하는 mutation
 */
export function useCompleteChatSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: number) => completeSession(sessionId),
    onSuccess: (session) => {
      queryClient.setQueryData(chatQueries.activeSession(), session);
      if (session.id) {
        queryClient.invalidateQueries({ queryKey: chatQueries.messages(session.id) });
      }
      queryClient.invalidateQueries({ queryKey: chatQueries.sessions() });
    },
  });
}

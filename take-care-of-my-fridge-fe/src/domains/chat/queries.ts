import {
  infiniteQueryOptions,
  keepPreviousData,
  type QueryClient,
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';

import type { ChatSessionListRequestStatusEnum, ChatSessionResponse } from '@/common/api/clients';

import { completeSession, getMessages, getMySessions, getSession, startSession } from './api';

export const chatQueries = {
  all: () => ['chat'] as const,
  activeSession: () => ['chat', 'activeSession'] as const,
  messages: (sessionId: number) => ['chat', 'messages', sessionId] as const,
  session: (sessionId: number) => ['chat', 'session', sessionId] as const,
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

  // 끝난 상담 하나. 요리 완료로 끝났으면 그 요리를 알려 준다. 끝난 상담은 바뀌지 않아 다시 받지 않는다.
  endedSessionQuery: (sessionId: number) =>
    queryOptions({
      queryKey: chatQueries.session(sessionId),
      queryFn: ({ signal }) => getSession(sessionId, signal),
      enabled: sessionId > 0,
      staleTime: Infinity,
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

// 채팅창을 닫을 때 부른다. 진행 중 상담 캐시가 끝난 상담(직접 끝냄·다른 창의 끝남 신호)이면 지워,
// 다시 열 때 POST로 새 상담을 받게 한다. 진행 중 상담은 그대로 두어 다시 열면 이어진다.
// 상담 내역 오른쪽 칸처럼 아직 이 캐시를 보고 있는 곳이 있으면 지우지 않는다(지우면 그 화면이 다시 받으며 새 상담이 생긴다).
export function removeEndedActiveSession(queryClient: QueryClient) {
  const queryKey = chatQueries.activeSession();
  const session = queryClient.getQueryData<ChatSessionResponse>(queryKey);
  if (session?.status !== 'COMPLETED' && session?.status !== 'EXPIRED') return;
  const observers =
    queryClient.getQueryCache().find({ queryKey, exact: true })?.getObserversCount() ?? 0;
  if (observers > 0) return;
  queryClient.removeQueries({ queryKey, exact: true });
}

// 상담 내역 화면을 떠날 때(오른쪽 칸이 사라질 때) 부른다. 화면이 사라질 때는 부모의 정리가 자식(오른쪽 칸)의
// 쿼리 구독 해제보다 먼저 돌아 아직 보는 곳이 남아 있으므로, 정리가 모두 끝난 뒤에 비운다.
// 이때는 캐시를 보는 화면이 없어 다시 받기(POST)가 나가지 않고, 다음에 채팅창을 열면 새 상담이 된다.
export function removeEndedActiveSessionAfterUnmount(queryClient: QueryClient) {
  queueMicrotask(() => removeEndedActiveSession(queryClient));
}

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

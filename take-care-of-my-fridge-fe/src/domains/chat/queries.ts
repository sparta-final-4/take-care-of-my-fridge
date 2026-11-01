import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';

import { completeSession, getMessages, startSession } from './api';

export const chatQueries = {
  all: () => ['chat'] as const,
  activeSession: () => ['chat', 'activeSession'] as const,
  messages: (sessionId: number) => ['chat', 'messages', sessionId] as const,

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
    },
  });
}

import { MutationCache, QueryClient } from '@tanstack/react-query';

import { getErrorMessage, isUnauthorized } from '@/common/api/errors';
import { sessionId } from '@/common/auth/sessionId';

import { getFeedback } from './feedback';

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      // 화면에서 실패를 직접 처리할 때 전역 알림을 끈다.
      skipGlobalError?: boolean;
    };
  }
}

// mutation이 시작된 세션. 이전 세션 mutation의 실패는 알리지 않는다.
const mutationSessions = new WeakMap<object, number>();

export const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onMutate: (_variables, mutation) => {
      mutationSessions.set(mutation, sessionId());
    },
    onError: (error, _variables, _onMutateResult, mutation) => {
      if (mutation.meta?.skipGlobalError) return;
      if (isUnauthorized(error) || mutationSessions.get(mutation) !== sessionId()) return;
      getFeedback()?.message.error(getErrorMessage(error));
    },
  }),
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

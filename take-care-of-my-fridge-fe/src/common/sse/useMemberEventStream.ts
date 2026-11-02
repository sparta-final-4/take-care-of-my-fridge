import { type QueryClient, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

// 회원 실시간 신호 하나에 이벤트 이름별 처리기를 도메인이 붙인다(notification, chat).
// 신호에는 내용이 없거나 식별자뿐이고, 내용은 각 도메인이 기존 조회 API로 받는다.
export interface MemberEventListener {
  event: string;
  onMessage: (queryClient: QueryClient, data: string) => void;
  // 끊겼다 다시 붙은 사이에 놓친 신호를 메우려고 다시 받을 것
  onReconnect: (queryClient: QueryClient) => void;
}

// 연결이 끊기면 브라우저가 알아서 다시 연결하고, 처음이 아닌 연결 때 처리기마다 한 번 다시 받는다.
export function openMemberEventStream(
  queryClient: QueryClient,
  listeners: readonly MemberEventListener[],
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  const source = new EventSource(`${baseUrl}/api/notifications/stream`, {
    withCredentials: true,
  });
  listeners.forEach((listener) => {
    source.addEventListener(listener.event, (event: MessageEvent<string>) => {
      listener.onMessage(queryClient, event.data);
    });
  });
  let connectedBefore = false;
  source.addEventListener('open', () => {
    if (connectedBefore) listeners.forEach((listener) => listener.onReconnect(queryClient));
    connectedBefore = true;
  });
  return () => source.close();
}

// 앱 전체에서 한 곳(헤더)만 부른다. listeners는 바뀌면 다시 연결하므로 모듈 상수로 넘긴다.
export function useMemberEventStream(enabled: boolean, listeners: readonly MemberEventListener[]) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    return openMemberEventStream(queryClient, listeners);
  }, [enabled, queryClient, listeners]);
}

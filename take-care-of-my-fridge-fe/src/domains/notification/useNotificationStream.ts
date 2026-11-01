import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import { notificationQueries } from './queries';

// 로그인한 동안 알림 스트림에 연결하고, 새 알림 신호가 오면 안 읽은 수와 열려 있는 목록을 다시 불러온다.
// 신호에는 알림 내용이 없고 내용은 기존 조회 API로 받는다.
// 연결이 끊기면 브라우저가 알아서 다시 연결하고, 다시 연결되면 한 번 다시 불러와 놓친 알림을 메운다.
export function useNotificationStream(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
    const source = new EventSource(`${baseUrl}/api/notifications/stream`, {
      withCredentials: true,
    });
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: notificationQueries.all });
    };
    source.addEventListener('notification', refresh);
    // 끊겼다 다시 붙은 사이에 놓친 신호가 있을 수 있어, 처음이 아닌 연결 때 한 번 다시 불러온다.
    let connectedBefore = false;
    source.addEventListener('open', () => {
      if (connectedBefore) refresh();
      connectedBefore = true;
    });
    return () => source.close();
  }, [enabled, queryClient]);
}

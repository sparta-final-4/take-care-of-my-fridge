import type { QueryClient } from '@tanstack/react-query';

import type { MemberEventListener } from '@/common/sse/useMemberEventStream';

import { notificationQueries } from './queries';

// 새 알림 신호가 오면 안 읽은 수와 열려 있는 목록을 다시 불러온다. 다시 연결될 때도 한 번 다시 불러와 놓친 알림을 메운다.
function refreshNotifications(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: notificationQueries.all });
}

export const notificationEventListener: MemberEventListener = {
  event: 'notification',
  onMessage: refreshNotifications,
  onReconnect: refreshNotifications,
};

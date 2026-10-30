import { infiniteQueryOptions, queryOptions } from '@tanstack/react-query';

import { getMyNotifications, getUnreadNotificationCount } from './api';

export const notificationQueries = {
  all: ['notifications'] as const,
  unreadCount: () =>
    queryOptions({
      queryKey: [...notificationQueries.all, 'unread-count'],
      queryFn: ({ signal }) => getUnreadNotificationCount(signal),
    }),
  // 더 보기는 받은 마지막 알림 ID 다음부터 이어 받는다.
  // 종을 닫으면 바로 지워, 다시 열 때 가장 최신 5개부터 새로 받는다.
  list: () =>
    infiniteQueryOptions({
      queryKey: [...notificationQueries.all, 'list'],
      queryFn: ({ pageParam, signal }) => getMyNotifications(pageParam, signal),
      initialPageParam: undefined as number | undefined,
      getNextPageParam: (lastPage) =>
        lastPage.hasNext ? lastPage.notifications?.at(-1)?.notificationId : undefined,
      gcTime: 0,
    }),
};

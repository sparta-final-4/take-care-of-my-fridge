import { QueryClient } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, type MockInstance, vi } from 'vitest';

import { notificationEventListener } from './notificationEvent';
import { notificationQueries } from './queries';

// 알림 SSE를 공통 연결로 옮기기 전(useNotificationStream)과 같은 동작인지 본다.
describe('notificationEventListener', () => {
  let queryClient: QueryClient;
  let invalidate: MockInstance<QueryClient['invalidateQueries']>;

  const invalidatedKeys = () => invalidate.mock.calls.map(([filters]) => filters?.queryKey);

  beforeEach(() => {
    queryClient = new QueryClient();
    invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  });

  it('notification 이벤트를 받는다', () => {
    expect(notificationEventListener.event).toBe('notification');
  });

  it('새 알림 신호가 오면 안 읽은 수와 목록(알림 쿼리 전체)만 다시 받는다', () => {
    notificationEventListener.onMessage(queryClient, 'refresh');

    expect(invalidatedKeys()).toEqual([notificationQueries.all]);
  });

  it('다시 연결되면 알림 쿼리 전체를 한 번 다시 받는다', () => {
    notificationEventListener.onReconnect(queryClient);

    expect(invalidatedKeys()).toEqual([notificationQueries.all]);
  });
});

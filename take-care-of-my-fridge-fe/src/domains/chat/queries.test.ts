import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatSessionResponse } from '@/common/api/clients';

import { startSession } from './api';
import {
  chatQueries,
  removeEndedActiveSession,
  removeEndedActiveSessionAfterUnmount,
} from './queries';

vi.mock('./api', () => ({
  startSession: vi.fn(),
  completeSession: vi.fn(),
  getMessages: vi.fn(),
  getMySessions: vi.fn(),
}));

describe('removeEndedActiveSession (채팅창을 닫을 때)', () => {
  let queryClient: QueryClient;

  const setActive = (session: ChatSessionResponse) =>
    queryClient.setQueryData(chatQueries.activeSession(), session);

  const getActive = () =>
    queryClient.getQueryData<ChatSessionResponse>(chatQueries.activeSession());

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it.each(['COMPLETED', 'EXPIRED'] as const)(
    '끝난 상담(%s)이면 진행 중 상담 캐시를 비워 다시 열 때 새 상담을 받게 한다',
    (status) => {
      setActive({ id: 15, status });

      removeEndedActiveSession(queryClient);

      expect(getActive()).toBeUndefined();
      expect(
        queryClient.getQueryCache().find({ queryKey: chatQueries.activeSession() }),
      ).toBeUndefined();
    },
  );

  it('진행 중 상담은 닫아도 그대로 두어 다시 열면 이어진다', () => {
    setActive({ id: 15, status: 'ACTIVE' });

    removeEndedActiveSession(queryClient);

    expect(getActive()).toEqual({ id: 15, status: 'ACTIVE' });
  });

  it('캐시가 없으면 아무것도 하지 않는다', () => {
    removeEndedActiveSession(queryClient);

    expect(getActive()).toBeUndefined();
  });

  it('상담 내역 오른쪽 칸처럼 아직 보고 있는 곳이 있으면 끝난 상담이어도 비우지 않는다', () => {
    setActive({ id: 15, status: 'COMPLETED' });
    const observer = new QueryObserver(queryClient, {
      queryKey: chatQueries.activeSession(),
      queryFn: () => new Promise<never>(() => {}),
      staleTime: Infinity,
    });
    const unsubscribe = observer.subscribe(() => {});

    removeEndedActiveSession(queryClient);
    unsubscribe();

    expect(getActive()).toEqual({ id: 15, status: 'COMPLETED' });
  });

  it('메시지 기록 등 다른 상담 쿼리는 건드리지 않는다', () => {
    setActive({ id: 15, status: 'COMPLETED' });
    queryClient.setQueryData(chatQueries.messages(15), []);

    removeEndedActiveSession(queryClient);

    expect(queryClient.getQueryData(chatQueries.messages(15))).toEqual([]);
  });
});

describe('removeEndedActiveSessionAfterUnmount (상담 내역 화면을 떠날 때)', () => {
  let queryClient: QueryClient;

  const flushCleanups = () => new Promise((resolve) => setTimeout(resolve, 0));

  // 상담 내역 오른쪽 칸처럼 진행 중 상담 캐시를 보는 화면
  const openRightColumn = () =>
    new QueryObserver(queryClient, chatQueries.activeSessionQuery()).subscribe(() => {});

  beforeEach(() => {
    queryClient = new QueryClient();
    vi.mocked(startSession).mockReset();
  });

  it('끝난 상담을 보다가 화면을 떠나면 POST 없이 캐시를 비우고, 다음에 채팅창을 열면 새 상담이 열린다', async () => {
    queryClient.setQueryData(chatQueries.activeSession(), { id: 15, status: 'COMPLETED' });
    const closeRightColumn = openRightColumn();

    // 화면이 사라질 때: 부모(상담 내역)의 정리가 먼저, 자식(오른쪽 칸)의 구독 해제가 나중에 돈다.
    removeEndedActiveSessionAfterUnmount(queryClient);
    closeRightColumn();
    await flushCleanups();

    expect(queryClient.getQueryData(chatQueries.activeSession())).toBeUndefined();
    expect(startSession).not.toHaveBeenCalled();

    // 다른 화면에서 채팅창을 열면 캐시가 없어 상담 시작(POST)으로 새 상담을 받는다.
    vi.mocked(startSession).mockResolvedValue({ id: 16, status: 'ACTIVE' });
    const opened = await queryClient.ensureQueryData(chatQueries.activeSessionQuery());

    expect(opened).toEqual({ id: 16, status: 'ACTIVE' });
    expect(startSession).toHaveBeenCalledOnce();
  });

  it('진행 중 상담을 보다가 떠나면 캐시를 그대로 두어 채팅창에서 이어진다', async () => {
    queryClient.setQueryData(chatQueries.activeSession(), { id: 15, status: 'ACTIVE' });
    const closeRightColumn = openRightColumn();

    removeEndedActiveSessionAfterUnmount(queryClient);
    closeRightColumn();
    await flushCleanups();

    const opened = await queryClient.ensureQueryData(chatQueries.activeSessionQuery());
    expect(opened).toEqual({ id: 15, status: 'ACTIVE' });
    expect(startSession).not.toHaveBeenCalled();
  });
});

import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, type MockInstance, vi } from 'vitest';

import type { ChatSessionResponse } from '@/common/api/clients';

import {
  chatSessionEventListener,
  handleChatSessionSignal,
  parseChatSessionSignal,
} from './chatSessionEvent';
import { useChatStreamingStore } from './model/useChatStreamingStore';
import { chatQueries } from './queries';

const signal = (sessionId: number, status: string) => JSON.stringify({ sessionId, status });

describe('parseChatSessionSignal', () => {
  it('상담 번호와 상태를 꺼낸다', () => {
    expect(parseChatSessionSignal(signal(15, 'COMPLETED'))).toEqual({
      sessionId: 15,
      status: 'COMPLETED',
    });
  });

  it('깨진 JSON, 모르는 상태, 빠진 필드는 무시한다', () => {
    expect(parseChatSessionSignal('not-json')).toBeNull();
    expect(parseChatSessionSignal(signal(15, 'UNKNOWN'))).toBeNull();
    expect(parseChatSessionSignal(JSON.stringify({ status: 'ACTIVE' }))).toBeNull();
    expect(parseChatSessionSignal('null')).toBeNull();
  });
});

describe('handleChatSessionSignal', () => {
  let queryClient: QueryClient;
  let invalidate: MockInstance<QueryClient['invalidateQueries']>;

  const invalidatedKeys = () => invalidate.mock.calls.map(([filters]) => filters?.queryKey);

  const setActive = (session: ChatSessionResponse) =>
    queryClient.setQueryData(chatQueries.activeSession(), session);

  const getActive = () =>
    queryClient.getQueryData<ChatSessionResponse>(chatQueries.activeSession());

  beforeEach(() => {
    queryClient = new QueryClient();
    invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  });

  afterEach(() => {
    useChatStreamingStore.setState({ streamingSessionId: null });
  });

  it('상담 내역과 그 상담의 대화를 다시 받는다', () => {
    handleChatSessionSignal(queryClient, signal(15, 'ACTIVE'));

    expect(invalidatedKeys()).toEqual([chatQueries.sessions(), chatQueries.messages(15)]);
  });

  it('이 창이 그 상담의 답을 받는 중이면 대화는 다시 받지 않는다', () => {
    useChatStreamingStore.getState().startStreaming(15);

    handleChatSessionSignal(queryClient, signal(15, 'ACTIVE'));

    expect(invalidatedKeys()).toEqual([chatQueries.sessions()]);
  });

  it('다른 상담의 답을 받는 중이면 신호 온 상담의 대화는 다시 받는다', () => {
    useChatStreamingStore.getState().startStreaming(16);

    handleChatSessionSignal(queryClient, signal(15, 'ACTIVE'));

    expect(invalidatedKeys()).toContainEqual(chatQueries.messages(15));
  });

  it('진행 중 상담이 끝났다는 신호면 진행 중 상담은 다시 받지 않고 상태만 바꾼다', () => {
    setActive({ id: 15, status: 'ACTIVE', createdAt: '2026-11-02T10:00:00' });

    handleChatSessionSignal(queryClient, signal(15, 'COMPLETED'));

    expect(getActive()).toEqual({ id: 15, status: 'COMPLETED', createdAt: '2026-11-02T10:00:00' });
    expect(invalidatedKeys()).not.toContainEqual(chatQueries.activeSession());
  });

  it('만료 신호도 같은 상담이면 상태만 바꾼다', () => {
    setActive({ id: 15, status: 'ACTIVE' });

    handleChatSessionSignal(queryClient, signal(15, 'EXPIRED'));

    expect(getActive()?.status).toBe('EXPIRED');
    expect(invalidatedKeys()).not.toContainEqual(chatQueries.activeSession());
  });

  it('다른 상담이 끝났다는 신호면 진행 중 상담을 건드리지 않는다', () => {
    setActive({ id: 16, status: 'ACTIVE' });

    handleChatSessionSignal(queryClient, signal(15, 'EXPIRED'));

    expect(getActive()).toEqual({ id: 16, status: 'ACTIVE' });
    expect(invalidatedKeys()).not.toContainEqual(chatQueries.activeSession());
  });

  it('다른 창에서 새 상담을 시작했으면 그때만 진행 중 상담을 다시 받는다', () => {
    setActive({ id: 15, status: 'COMPLETED' });

    handleChatSessionSignal(queryClient, signal(16, 'ACTIVE'));

    expect(invalidatedKeys()).toContainEqual(chatQueries.activeSession());
  });

  it('같은 상담에 답이 저장된 신호면 진행 중 상담은 다시 받지 않는다', () => {
    setActive({ id: 15, status: 'ACTIVE' });

    handleChatSessionSignal(queryClient, signal(15, 'ACTIVE'));

    expect(invalidatedKeys()).not.toContainEqual(chatQueries.activeSession());
  });

  it('진행 중 상담을 아직 받은 적이 없으면 새로 받지 않는다(채팅창을 열 때 받는다)', () => {
    handleChatSessionSignal(queryClient, signal(16, 'ACTIVE'));

    expect(getActive()).toBeUndefined();
    expect(invalidatedKeys()).not.toContainEqual(chatQueries.activeSession());
  });

  it('깨진 신호면 아무것도 하지 않는다', () => {
    handleChatSessionSignal(queryClient, 'not-json');

    expect(invalidate).not.toHaveBeenCalled();
  });
});

describe('chatSessionEventListener.onReconnect', () => {
  let queryClient: QueryClient;
  const unsubscribes: (() => void)[] = [];

  const isInvalidated = (queryKey: readonly unknown[]) =>
    queryClient.getQueryState(queryKey)?.isInvalidated ?? false;

  // 화면에 열려 있는 대화처럼 관찰자를 붙인다. 다시 받기가 끝나 표시가 지워지지 않게 응답은 오지 않게 둔다.
  const openConversation = (sessionId: number) => {
    const observer = new QueryObserver(queryClient, {
      queryKey: chatQueries.messages(sessionId),
      queryFn: () => new Promise<never>(() => {}),
      staleTime: Infinity,
    });
    unsubscribes.push(observer.subscribe(() => {}));
  };

  beforeEach(() => {
    queryClient = new QueryClient();
    [15, 16, 17].forEach((id) => queryClient.setQueryData(chatQueries.messages(id), []));
  });

  afterEach(() => {
    unsubscribes.splice(0).forEach((unsubscribe) => unsubscribe());
    useChatStreamingStore.setState({ streamingSessionId: null });
  });

  it('상담 내역과 열려 있는 대화를 다시 받고, 답을 받는 중인 대화와 닫힌 대화는 건너뛴다', () => {
    queryClient.setQueryData([...chatQueries.sessions(), { status: 'ALL', size: 10 }], {});
    openConversation(15);
    openConversation(16);
    useChatStreamingStore.getState().startStreaming(16);

    chatSessionEventListener.onReconnect(queryClient);

    expect(isInvalidated([...chatQueries.sessions(), { status: 'ALL', size: 10 }])).toBe(true);
    expect(isInvalidated(chatQueries.messages(15))).toBe(true);
    expect(isInvalidated(chatQueries.messages(16))).toBe(false);
    expect(isInvalidated(chatQueries.messages(17))).toBe(false);
  });

  it('진행 중 상담은 다시 받지 않는다(POST라 새 상담이 생길 수 있음)', () => {
    queryClient.setQueryData(chatQueries.activeSession(), { id: 15, status: 'ACTIVE' });

    chatSessionEventListener.onReconnect(queryClient);

    expect(isInvalidated(chatQueries.activeSession())).toBe(false);
  });
});

import { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type MemberEventListener, openMemberEventStream } from './useMemberEventStream';

// 브라우저 EventSource 대신 붙은 처리기를 직접 불러 보는 가짜
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readonly listeners = new Map<string, ((event: { data: string }) => void)[]>();
  readonly url: string;
  readonly init?: EventSourceInit;
  closed = false;

  constructor(url: string, init?: EventSourceInit) {
    this.url = url;
    this.init = init;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: { data: string }) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  emit(type: string, data = '') {
    this.listeners.get(type)?.forEach((listener) => listener({ data }));
  }

  close() {
    this.closed = true;
  }
}

function fakeListener(event: string) {
  return {
    event,
    onMessage: vi.fn<MemberEventListener['onMessage']>(),
    onReconnect: vi.fn<MemberEventListener['onReconnect']>(),
  };
}

describe('openMemberEventStream', () => {
  let queryClient: QueryClient;
  let alpha: ReturnType<typeof fakeListener>;
  let beta: ReturnType<typeof fakeListener>;

  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    queryClient = new QueryClient();
    alpha = fakeListener('alpha');
    beta = fakeListener('beta');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('알림 스트림 하나를 쿠키와 함께 열고, 닫으면 연결을 닫는다', () => {
    const close = openMemberEventStream(queryClient, [alpha, beta]);

    expect(FakeEventSource.instances).toHaveLength(1);
    const [source] = FakeEventSource.instances;
    expect(source?.url).toBe('/api/notifications/stream');
    expect(source?.init).toEqual({ withCredentials: true });

    close();
    expect(source?.closed).toBe(true);
  });

  it('이벤트 이름에 맞는 처리기에만 data를 넘긴다', () => {
    openMemberEventStream(queryClient, [alpha, beta]);

    FakeEventSource.instances[0]?.emit('beta', '{"id":1}');

    expect(beta.onMessage).toHaveBeenCalledWith(queryClient, '{"id":1}');
    expect(alpha.onMessage).not.toHaveBeenCalled();
  });

  it('처음 연결 때는 다시 받지 않고, 다시 연결되면 모든 처리기가 한 번씩 다시 받는다', () => {
    openMemberEventStream(queryClient, [alpha, beta]);
    const source = FakeEventSource.instances[0];

    source?.emit('open');
    expect(alpha.onReconnect).not.toHaveBeenCalled();
    expect(beta.onReconnect).not.toHaveBeenCalled();

    source?.emit('open');
    expect(alpha.onReconnect).toHaveBeenCalledExactlyOnceWith(queryClient);
    expect(beta.onReconnect).toHaveBeenCalledExactlyOnceWith(queryClient);
  });
});

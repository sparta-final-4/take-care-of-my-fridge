import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { queryClient } from '@/app/queryClient';

import { useAuthStore } from './authStore';
import { setSession } from './session';

vi.mock('@/app/queryClient', async () => {
  const { QueryClient } = await import('@tanstack/react-query');
  return { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) };
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe('setSession', () => {
  beforeEach(() => {
    (queryClient as QueryClient).clear();
    useAuthStore.setState({ status: 'initializing', member: null, reason: null });
  });

  it('로그인 확인 중 화면이 받던 쿼리는 세션이 정해진 뒤 다시 받는다', async () => {
    const first = deferred<string>();
    const queryFn = vi
      .fn<() => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce('목록');
    const observer = new QueryObserver(queryClient, { queryKey: ['recipes'], queryFn });
    const unsubscribe = observer.subscribe(() => undefined);

    await setSession(null);
    first.resolve('취소된 응답');

    await vi.waitFor(() => expect(observer.getCurrentResult().data).toBe('목록'));
    expect(queryFn).toHaveBeenCalledTimes(2);
    expect(useAuthStore.getState().status).toBe('anonymous');
    unsubscribe();
  });

  it('이전 세션 데이터는 남기지 않는다', async () => {
    queryClient.setQueryData(['cart'], ['이전 회원 장바구니']);
    queryClient.setQueryData(['me'], '이전 회원');
    const queryFn = vi.fn<() => Promise<string>>().mockResolvedValue('새 데이터');
    // 신선한 데이터로 두어 구독만으로는 다시 받지 않게 한다.
    const observer = new QueryObserver(queryClient, {
      queryKey: ['me'],
      queryFn,
      staleTime: Infinity,
    });
    const unsubscribe = observer.subscribe(() => undefined);

    await setSession(null);

    // 재요청을 기다리지 않아도 이전 데이터는 setSession이 끝날 때 이미 비어 있다.
    expect(queryClient.getQueryData(['cart'])).toBeUndefined();
    expect(queryClient.getQueryData(['me'])).toBeUndefined();
    await vi.waitFor(() => expect(observer.getCurrentResult().data).toBe('새 데이터'));
    expect(queryFn).toHaveBeenCalledOnce();
    unsubscribe();
  });

  // 로그인 상태를 먼저 바꾼 뒤 다시 받아야, status에 따라 켜지는 쿼리가 새 상태 기준으로 요청된다.
  describe('로그인 상태에 따라 켜지는 쿼리', () => {
    const authenticated = () => useAuthStore.getState().status === 'authenticated';

    it('로그인하면 다시 받는다', async () => {
      const queryFn = vi.fn<() => Promise<string>>().mockResolvedValue('장바구니');
      const observer = new QueryObserver(queryClient, {
        queryKey: ['cart'],
        queryFn,
        enabled: authenticated,
      });
      const unsubscribe = observer.subscribe(() => undefined);
      expect(queryFn).not.toHaveBeenCalled();

      await setSession({ memberId: 1, role: 'USER' } as Parameters<typeof setSession>[0]);

      await vi.waitFor(() => expect(observer.getCurrentResult().data).toBe('장바구니'));
      expect(queryFn).toHaveBeenCalledOnce();
      unsubscribe();
    });

    it('로그아웃하면 요청하지 않는다', async () => {
      useAuthStore.setState({ status: 'authenticated' });
      queryClient.setQueryData(['cart'], '이전 회원 장바구니');
      const queryFn = vi.fn<() => Promise<string>>().mockResolvedValue('장바구니');
      const observer = new QueryObserver(queryClient, {
        queryKey: ['cart'],
        queryFn,
        enabled: authenticated,
        staleTime: Infinity,
      });
      const unsubscribe = observer.subscribe(() => undefined);

      await setSession(null);
      await new Promise((resolve) => setTimeout(resolve));

      expect(queryFn).not.toHaveBeenCalled();
      expect(observer.getCurrentResult().data).toBeUndefined();
      unsubscribe();
    });
  });
});

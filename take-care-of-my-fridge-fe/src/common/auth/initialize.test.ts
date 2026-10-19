import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { refresh } from '@/domains/auth/api';
import { getMyInfo } from '@/domains/member/api';

import { useAuthStore } from './authStore';
import { initSession } from './initialize';
import { setSession } from './session';

vi.mock('@/domains/auth/api', () => ({ refresh: vi.fn() }));
vi.mock('@/domains/member/api', () => ({ getMyInfo: vi.fn() }));
vi.mock('./authStore', () => ({ useAuthStore: { setState: vi.fn() } }));
vi.mock('./session', () => ({ setSession: vi.fn() }));
vi.mock('./sessionId', () => ({ sessionId: () => 0 }));

function unauthorized(code: string) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('unauthorized', undefined, config, undefined, {
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
    data: { success: false, data: null, error: { code, message: '인증 오류' } },
  });
}

describe('initSession', () => {
  beforeEach(() => vi.clearAllMocks());

  it('정지된 계정은 재발급하지 않고 사유를 보존한다', async () => {
    vi.mocked(getMyInfo).mockRejectedValueOnce(unauthorized('A006'));

    await initSession();

    expect(refresh).not.toHaveBeenCalled();
    expect(setSession).toHaveBeenCalledWith(null, 'suspended');
  });

  it('만료된 access 쿠키는 재발급 후 내 정보를 다시 조회한다', async () => {
    const member = { memberId: 1, role: 'USER' as const };
    vi.mocked(getMyInfo)
      .mockRejectedValueOnce(unauthorized('A003'))
      .mockResolvedValueOnce(member as Awaited<ReturnType<typeof getMyInfo>>);
    vi.mocked(refresh).mockResolvedValueOnce(undefined);

    await initSession();

    expect(refresh).toHaveBeenCalledOnce();
    expect(setSession).toHaveBeenCalledWith(member);
    expect(useAuthStore.setState).toHaveBeenCalledWith({ status: 'initializing' });
  });
});

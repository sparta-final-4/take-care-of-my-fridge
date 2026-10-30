import type { AxiosAdapter } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { axiosInstance } from '@/common/api/axiosInstance';
import { login } from '@/domains/auth/api';

import { changeTastes, checkEmailAvailability } from './api';

describe('회원·인증 API', () => {
  const adapter = vi.fn<AxiosAdapter>();
  const originalAdapter = axiosInstance.defaults.adapter;

  function respond(data: unknown) {
    adapter.mockImplementation(async (config) => ({
      data: { success: true, data, error: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }));
  }

  beforeEach(() => {
    axiosInstance.defaults.adapter = adapter;
    // 변경 요청 전에 CSRF 쿠키 발급 요청이 나가지 않게 쿠키를 둔다.
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=token' });
  });

  afterEach(() => {
    adapter.mockReset();
    vi.unstubAllGlobals();
    axiosInstance.defaults.adapter = originalAdapter;
  });

  it('이메일 중복 확인은 쿼리로 보내고 사용 가능 여부만 돌려준다', async () => {
    respond({ available: true });

    await expect(checkEmailAvailability('a+b@example.com')).resolves.toBe(true);
    expect(adapter.mock.calls[0][0].url).toBe(
      '/api/members/email-availability?email=a%2Bb%40example.com',
    );
  });

  it('맛 선호 변경은 tastes 본문으로 보낸다', async () => {
    respond(null);

    await changeTastes(['SALTY', 'MILD']);
    const config = adapter.mock.calls[0][0];
    expect([config.method, config.url]).toEqual(['put', '/api/members/me/tastes']);
    expect(JSON.parse(config.data as string)).toEqual({ tastes: ['SALTY', 'MILD'] });
  });

  it('로그인은 회원 정보를 돌려준다', async () => {
    respond({ memberId: 1, role: 'USER' });

    await expect(login({ email: 'a@example.com', password: 'Passw0rd!' })).resolves.toEqual({
      memberId: 1,
      role: 'USER',
    });
    expect(adapter.mock.calls[0][0].url).toBe('/api/auth/login');
  });
});

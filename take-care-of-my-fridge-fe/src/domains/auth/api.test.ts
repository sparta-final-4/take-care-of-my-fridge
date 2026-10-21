import { afterEach, describe, expect, it, vi } from 'vitest';

import { axiosInstance } from '@/common/api/axiosInstance';

import { csrfHeaders } from './api';

vi.mock('@/common/api/axiosInstance', () => ({ axiosInstance: { get: vi.fn() } }));

describe('csrfHeaders', () => {
  afterEach(() => vi.unstubAllGlobals());

  function respond() {
    vi.mocked(axiosInstance.get).mockResolvedValue({
      data: { success: true, data: { headerName: 'X-XSRF-TOKEN', token: 'masked' }, error: null },
    });
  }

  it('XSRF-TOKEN 쿠키의 원본 토큰을 헤더로 보낸다', async () => {
    respond();
    vi.stubGlobal('document', { cookie: 'other=1; XSRF-TOKEN=raw%2Dtoken' });

    await expect(csrfHeaders()).resolves.toEqual({ 'X-XSRF-TOKEN': 'raw-token' });
  });

  it('쿠키를 읽을 수 없으면 응답 body의 토큰을 쓴다', async () => {
    respond();
    vi.stubGlobal('document', { cookie: '' });

    await expect(csrfHeaders()).resolves.toEqual({ 'X-XSRF-TOKEN': 'masked' });
  });
});

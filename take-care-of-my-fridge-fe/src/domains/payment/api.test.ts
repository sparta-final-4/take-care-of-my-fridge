import type { AxiosAdapter } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { axiosInstance } from '@/common/api/axiosInstance';

import { getPinStatus, payWithCard, resetPin } from './api';

describe('결제 API', () => {
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

  it('등록 카드 결제는 카드와 결제 비밀번호를 함께 보낸다', async () => {
    respond({ orderId: 3 });

    await payWithCard(3, 7, '135790');

    const config = adapter.mock.calls[0][0];
    expect(config.method).toBe('post');
    expect(config.url).toBe('/api/orders/3/payments/billing-key');
    expect(JSON.parse(String(config.data))).toEqual({ cardId: 7, pin: '135790' });
  });

  it('결제 비밀번호 상태는 응답 래퍼를 벗겨 돌려준다', async () => {
    respond({ isSet: true, isLocked: false, remainingAttempts: 5 });

    await expect(getPinStatus()).resolves.toEqual({
      isSet: true,
      isLocked: false,
      remainingAttempts: 5,
    });
    expect(adapter.mock.calls[0][0].url).toBe('/api/payment-pin');
  });

  it('재설정은 로그인 비밀번호와 새 비밀번호를 보낸다', async () => {
    respond(null);

    await resetPin({ loginPassword: 'password1!', newPin: '135790' });

    const config = adapter.mock.calls[0][0];
    expect(config.method).toBe('post');
    expect(config.url).toBe('/api/payment-pin/reset');
    expect(JSON.parse(String(config.data))).toEqual({
      loginPassword: 'password1!',
      newPin: '135790',
    });
  });
});

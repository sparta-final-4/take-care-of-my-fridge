import type { AxiosAdapter } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { axiosInstance } from '@/common/api/axiosInstance';

import { getForecast } from './api';

describe('getForecast', () => {
  const adapter = vi.fn<AxiosAdapter>();
  const originalAdapter = axiosInstance.defaults.adapter;
  const forecast = { weekStart: '2026-11-02', items: [{ productId: 1, predictedRank: 1 }] };

  beforeEach(() => {
    adapter.mockImplementation(async (config) => ({
      data: { success: true, data: forecast, error: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }));
    axiosInstance.defaults.adapter = adapter;
  });

  afterEach(() => {
    adapter.mockReset();
    axiosInstance.defaults.adapter = originalAdapter;
  });

  it('주를 고르지 않으면 최신 예측을 읽는다', async () => {
    await expect(getForecast()).resolves.toEqual(forecast);
    expect(adapter.mock.calls[0][0].url).toBe('/api/admin/forecasts/latest');
  });

  it('고른 주의 예측을 읽는다', async () => {
    await getForecast('2026-11-02');
    expect(adapter.mock.calls[0][0].url).toBe('/api/admin/forecasts/2026-11-02');
  });
});

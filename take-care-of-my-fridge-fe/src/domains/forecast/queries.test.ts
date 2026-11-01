import { AxiosError, AxiosHeaders } from 'axios';
import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';

import type { ForecastResponse } from '@/common/api/clients';

import { forecastQueries } from './queries';

function responseError(status: number, code: string) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('failed', undefined, config, undefined, {
    status,
    statusText: '',
    headers: {},
    config,
    data: { success: false, data: null, error: { code, message: '오류' } },
  });
}

describe('forecastQueries.detail', () => {
  const { retry } = forecastQueries.detail('2026-10-05');
  if (typeof retry !== 'function') throw new Error('retry는 함수여야 합니다.');

  it('예측 없음(F001)은 재시도하지 않는다', () => {
    expect(retry(0, responseError(404, 'F001'))).toBe(false);
  });

  it('시연 결과에서 실제 최신 주로 이동할 때 이전 목록을 유지하지 않는다', () => {
    const client = new QueryClient();
    const demoResult = { syntheticData: true } as ForecastResponse;
    client.setQueryData(['forecast', 'demo'], demoResult);
    const previousQuery = client.getQueryCache().find({ queryKey: ['forecast', 'demo'] });
    const { placeholderData } = forecastQueries.detail();
    if (typeof placeholderData !== 'function' || !previousQuery) {
      throw new Error('이전 조회 조건이 필요합니다.');
    }

    expect(
      placeholderData(
        demoResult,
        previousQuery as unknown as Parameters<typeof placeholderData>[1],
      ),
    ).toBeUndefined();
  });

  it('그 외 오류는 한 번만 재시도한다', () => {
    const error = responseError(500, 'COMMON_002');
    expect(retry(0, error)).toBe(true);
    expect(retry(1, error)).toBe(false);
  });
});

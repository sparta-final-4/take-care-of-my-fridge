import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';

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

  it('그 외 오류는 한 번만 재시도한다', () => {
    const error = responseError(500, 'COMMON_002');
    expect(retry(0, error)).toBe(true);
    expect(retry(1, error)).toBe(false);
  });
});

import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';

import { getErrorMessage } from './errors';

function responseError(status: number, data: unknown) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('failed', undefined, config, undefined, {
    status,
    statusText: '',
    headers: {},
    config,
    data,
  });
}

describe('getErrorMessage', () => {
  it('공통 실패 응답의 서버 메시지를 쓴다', () => {
    const error = responseError(400, {
      success: false,
      data: null,
      error: { code: 'C001', message: '잘못된 입력값입니다.' },
    });
    expect(getErrorMessage(error)).toBe('잘못된 입력값입니다.');
  });

  it('5xx는 서버 메시지 대신 서버 오류 안내를 쓴다', () => {
    const error = responseError(500, {
      success: false,
      data: null,
      error: { code: 'C002', message: 'NullPointerException' },
    });
    expect(getErrorMessage(error)).toBe('서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.');
  });

  it('응답이 없으면 네트워크 안내, 타임아웃이면 지연 안내를 쓴다', () => {
    expect(getErrorMessage(new AxiosError('Network Error', 'ERR_NETWORK'))).toBe(
      '네트워크 연결을 확인해 주세요.',
    );
    expect(getErrorMessage(new AxiosError('timeout', 'ECONNABORTED'))).toBe(
      '응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.',
    );
  });

  it('axios 오류가 아니면 기본 안내를 쓴다', () => {
    expect(getErrorMessage(new Error('boom'))).toBe(
      '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    );
  });
});

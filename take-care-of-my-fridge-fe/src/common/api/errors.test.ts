import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';

import {
  getChatErrorInfo,
  getErrorCode,
  getErrorMessage,
  getImageUploadErrorMessage,
} from './errors';

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
    expect(getErrorCode(error)).toBe('C001');
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

describe('getImageUploadErrorMessage', () => {
  it('IMAGE_001(503)은 서버 오류 대신 URL 직접 입력을 안내한다', () => {
    const error = responseError(503, {
      success: false,
      data: null,
      error: { code: 'IMAGE_001', message: '업로드 비활성' },
    });
    expect(getImageUploadErrorMessage(error)).toBe(
      '이 환경에서는 이미지 업로드를 쓸 수 없습니다. 이미지 URL을 직접 입력해 주세요.',
    );
  });

  it('그 외 API 오류는 공통 메시지를 쓴다', () => {
    const error = responseError(400, {
      success: false,
      data: null,
      error: { code: 'IMAGE_002', message: 'JPG, PNG, WEBP 이미지만 올릴 수 있습니다.' },
    });
    expect(getImageUploadErrorMessage(error)).toBe('JPG, PNG, WEBP 이미지만 올릴 수 있습니다.');
  });

  it('S3 PUT 실패(일반 Error)는 업로드 실패를 안내한다', () => {
    expect(getImageUploadErrorMessage(new Error('image upload failed: 403'))).toBe(
      '이미지를 올리지 못했습니다. 잠시 후 다시 시도해 주세요.',
    );
  });
});

describe('getChatErrorInfo', () => {
  it('CHAT_009(다른 창에서 답을 받는 중)는 안내하고 보내려던 내용을 되돌린다', () => {
    expect(getChatErrorInfo('CHAT_009')).toEqual({
      message: '다른 창에서 답을 받는 중이에요. 답이 끝나면 다시 보내 주세요',
      action: 'restoreInput',
    });
  });

  it.each([
    ['CHAT_010', 'AI가 잠시 답할 수 없어요. 잠시 후 다시 시도해 주세요'],
    ['CHAT_011', '요청이 많아요. 잠시 후 다시 보내 주세요'],
  ])('%s(AI가 답을 시작하지 못함)는 안내하고 보내려던 내용을 되돌린다', (code, message) => {
    expect(getChatErrorInfo(code)).toEqual({ message, action: 'restoreInput' });
  });

  it('CHAT_012(답을 받다가 끊김)는 안내만 하고 입력은 되돌리지 않는다', () => {
    expect(getChatErrorInfo('CHAT_012')).toEqual({
      message: '답을 받다가 끊겼어요. 다시 보내 주세요',
    });
  });
});

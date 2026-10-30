import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useAuthStore } from '@/common/auth/authStore';
import { setSession } from '@/common/auth/session';
import { refresh } from '@/domains/auth/api';
import { streamMessage } from './streamMessage';

vi.mock('@/domains/auth/api', () => ({
  refresh: vi.fn(),
}));

vi.mock('@/common/auth/session', () => ({
  setSession: vi.fn(),
}));

describe('streamMessage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.setState({ status: 'anonymous', member: null });
  });

  it('JSON 에러 응답(application/json)을 수신하면 onError를 호출한다', async () => {
    const mockJson = {
      success: false,
      error: { code: 'CHAT_006', message: '상담 세션이 만료되었습니다.' },
    };

    const mockResponse = {
      ok: false,
      status: 409,
      headers: {
        get: vi.fn().mockReturnValue('application/json'),
      },
      json: vi.fn().mockResolvedValue(mockJson),
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (url: string) => {
        if (url.includes('/api/auth/csrf')) {
          return { ok: true, status: 200, headers: { get: () => 'application/json' } };
        }
        return mockResponse;
      }),
    );

    const onChunk = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await streamMessage({
      sessionId: 1,
      content: '안녕',
      onChunk,
      onDone,
      onError,
    });

    expect(onError).toHaveBeenCalledWith({
      code: 'CHAT_006',
      message: '상담 세션이 만료되었습니다.',
    });
    expect(onChunk).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });

  it('text/event-stream 응답 수신 시 message 조각을 JSON 파싱하여 앞 공백을 보존하고 done 시 onDone을 호출한다', async () => {
    const sseData =
      'event:message\ndata:{"text":"된장"}\n\n' +
      'event:message\ndata:{"text":" 찌개"}\n\n' +
      'event:done\ndata:{"userMessageId":10,"assistantMessageId":11}\n\n';

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(sseData));
        controller.close();
      },
    });

    const mockResponse = {
      ok: true,
      status: 200,
      headers: {
        get: vi.fn().mockReturnValue('text/event-stream'),
      },
      body: stream,
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (url: string) => {
        if (url.includes('/api/auth/csrf')) {
          return { ok: true, status: 200, headers: { get: () => 'application/json' } };
        }
        return mockResponse;
      }),
    );

    const onChunk = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await streamMessage({
      sessionId: 1,
      content: '된장찌개',
      onChunk,
      onDone,
      onError,
    });

    expect(onChunk).toHaveBeenCalledWith('된장');
    expect(onChunk).toHaveBeenCalledWith(' 찌개');
    expect(onDone).toHaveBeenCalledWith({ userMessageId: 10, assistantMessageId: 11 });
    expect(onError).not.toHaveBeenCalled();
  });

  it('스트림 내에서 error 이벤트 수신 시 onError({ code })를 호출한다', async () => {
    const sseData = 'event:error\ndata:{"code":"CHAT_007"}\n\n';

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(sseData));
        controller.close();
      },
    });

    const mockResponse = {
      ok: true,
      status: 200,
      headers: {
        get: vi.fn().mockReturnValue('text/event-stream'),
      },
      body: stream,
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (url: string) => {
        if (url.includes('/api/auth/csrf')) {
          return { ok: true, status: 200, headers: { get: () => 'application/json' } };
        }
        return mockResponse;
      }),
    );

    const onChunk = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await streamMessage({
      sessionId: 1,
      content: '질문',
      onChunk,
      onDone,
      onError,
    });

    expect(onError).toHaveBeenCalledWith({ code: 'CHAT_007' });
  });

  it('스트림이 done이나 error 없이 중간에 닫히면 onError("답을 끝까지 받지 못했어요")를 호출한다', async () => {
    const sseData = 'event:message\ndata:{"text":"된장"}\n\n';

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(sseData));
        controller.close();
      },
    });

    const mockResponse = {
      ok: true,
      status: 200,
      headers: {
        get: vi.fn().mockReturnValue('text/event-stream'),
      },
      body: stream,
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (url: string) => {
        if (url.includes('/api/auth/csrf')) {
          return { ok: true, status: 200, headers: { get: () => 'application/json' } };
        }
        return mockResponse;
      }),
    );

    const onChunk = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await streamMessage({
      sessionId: 1,
      content: '된장',
      onChunk,
      onDone,
      onError,
    });

    expect(onChunk).toHaveBeenCalledWith('된장');
    expect(onDone).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledWith({ message: '답을 끝까지 받지 못했어요' });
  });

  it('401 발생 시 로그인 상태이면 refresh() 후 재시도한다', async () => {
    useAuthStore.setState({
      status: 'authenticated',
      member: {
        memberId: 1,
        email: 'test@example.com',
        nickname: 'user',
        role: 'USER',
        status: 'ACTIVE',
        tastes: [],
        createdAt: '2026-01-01T00:00:00Z',
      },
    });

    const mock401Response = {
      ok: false,
      status: 401,
      headers: {
        get: vi.fn().mockReturnValue('application/json'),
      },
      json: vi.fn().mockResolvedValue({ error: { code: 'A003' } }),
    };

    const sseData =
      'event:message\ndata:{"text":"성공"}\n\n' + 'event:done\ndata:{"userMessageId":1}\n\n';

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(sseData));
        controller.close();
      },
    });

    const mockSuccessResponse = {
      ok: true,
      status: 200,
      headers: {
        get: vi.fn().mockReturnValue('text/event-stream'),
      },
      body: stream,
    };

    let postAttempt = 0;
    const fetchMock = vi.fn().mockImplementation(async (url: string) => {
      if (url.includes('/api/auth/csrf')) {
        return { ok: true, status: 200, headers: { get: () => 'application/json' } };
      }
      postAttempt += 1;
      return postAttempt === 1 ? mock401Response : mockSuccessResponse;
    });

    vi.stubGlobal('fetch', fetchMock);
    vi.mocked(refresh).mockResolvedValue();

    const onChunk = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await streamMessage({
      sessionId: 1,
      content: '재시도 테스트',
      onChunk,
      onDone,
      onError,
    });

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(postAttempt).toBe(2);
    expect(onChunk).toHaveBeenCalledWith('성공');
    expect(onDone).toHaveBeenCalledWith({ userMessageId: 1 });
    expect(onError).not.toHaveBeenCalled();
  });

  it('401 발생 시 refresh() 실패 시 setSession(null) 및 onError를 호출한다', async () => {
    useAuthStore.setState({
      status: 'authenticated',
      member: {
        memberId: 1,
        email: 'test@example.com',
        nickname: 'user',
        role: 'USER',
        status: 'ACTIVE',
        tastes: [],
        createdAt: '2026-01-01T00:00:00Z',
      },
    });

    const mock401Response = {
      ok: false,
      status: 401,
      headers: {
        get: vi.fn().mockReturnValue('application/json'),
      },
      json: vi.fn().mockResolvedValue({ error: { code: 'A003', message: '인증이 필요합니다.' } }),
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (url: string) => {
        if (url.includes('/api/auth/csrf')) {
          return { ok: true, status: 200, headers: { get: () => 'application/json' } };
        }
        return mock401Response;
      }),
    );
    vi.mocked(refresh).mockRejectedValue(new Error('Refresh failed'));

    const onChunk = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await streamMessage({
      sessionId: 1,
      content: '실패 테스트',
      onChunk,
      onDone,
      onError,
    });

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(setSession).toHaveBeenCalledWith(null, 'expired');
    expect(onError).toHaveBeenCalledWith({
      code: 'A003',
      message: '인증이 필요합니다.',
    });
  });
});

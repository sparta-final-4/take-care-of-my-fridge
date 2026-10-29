import { getErrorCode } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import { setSession } from '@/common/auth/session';
import { refresh } from '@/domains/auth/api';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export interface StreamMessageOptions {
  sessionId: number;
  content: string;
  signal?: AbortSignal;
  onChunk: (chunk: string) => void;
  onDone: (data: { userMessageId?: number; assistantMessageId?: number }) => void;
  onError: (error: { code?: string; message?: string }) => void;
}

function getXsrfToken(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

async function ensureXsrfToken(): Promise<string | undefined> {
  let token = getXsrfToken();
  if (!token) {
    try {
      await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: 'include' });
      token = getXsrfToken();
    } catch {
      // CSRF 요청 실패 시에도 헤더 없이 계속 진행
    }
  }
  return token;
}

/**
 * 상담 메시지를 전송하고 SSE 스트림으로 AI 답변 조각을 수신한다.
 * - 401 Unauthorized 수신 시 refresh() 호출 후 1회 재시도 (세션 만료 대응)
 * - JSON 에러 응답 수신 시 onError({ code, message }) 호출
 * - text/event-stream 수신 시 청크 단위로 SSE 파싱하여 onChunk / onDone / onError 호출
 * - 스트림이 done/error 없이 비정상 종료되면 onError("답을 끝까지 받지 못했어요") 호출
 */
export async function streamMessage({
  sessionId,
  content,
  signal,
  onChunk,
  onDone,
  onError,
}: StreamMessageOptions): Promise<void> {
  async function executeRequest(isRetry = false): Promise<Response | null> {
    const xsrfToken = await ensureXsrfToken();

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    };

    if (xsrfToken) {
      headers['X-XSRF-TOKEN'] = xsrfToken;
    }

    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL}/api/chat-sessions/${sessionId}/messages`, {
        method: 'POST',
        headers,
        credentials: 'include',
        body: JSON.stringify({ content }),
        signal,
      });
    } catch (err: unknown) {
      if (signal?.aborted) return null;
      onError({
        message: err instanceof Error ? err.message : '네트워크 오류가 발생했습니다.',
      });
      return null;
    }

    // 401 Unauthorized 시 토큰 재발급 후 1회 재시도
    if (response.status === 401 && !isRetry) {
      if (useAuthStore.getState().status === 'authenticated') {
        try {
          await refresh();
          return await executeRequest(true);
        } catch (refreshErr) {
          const reason = getErrorCode(refreshErr) === 'A006' ? 'suspended' : 'expired';
          await setSession(null, reason);
        }
      }
    }

    return response;
  }

  const response = await executeRequest();
  if (!response) return;

  const contentType = response.headers.get('content-type') ?? '';

  // 1. JSON 에러 응답 처리 (검증 실패, 401, 404, 409, 429, 503 등)
  if (contentType.includes('application/json') || !response.ok) {
    if (response.status === 401) {
      if (useAuthStore.getState().status === 'authenticated') {
        await setSession(null, 'expired');
      }
    }
    try {
      const json = await response.json();
      onError({
        code: json.error?.code,
        message: json.error?.message,
      });
    } catch {
      onError({
        code: response.status === 401 ? 'A003' : undefined,
        message:
          response.status === 401
            ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
            : '서버 에러가 발생했습니다.',
      });
    }
    return;
  }

  // 2. SSE 스트림 본문 읽기
  if (!response.body) {
    onError({ message: '응답 스트림을 읽을 수 없습니다.' });
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let isStreamCompleted = false;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() ?? '';

      for (const block of blocks) {
        if (!block.trim()) continue;

        let eventType = 'message';
        let data = '';

        const lines = block.split(/\r?\n/);
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            // data: 뒤의 내용을 그대로 보존 (공백 자르지 않음)
            const raw = line.slice(5);
            data += (data ? '\n' : '') + raw;
          }
        }

        if (eventType === 'message') {
          let text = data;
          try {
            const parsed = JSON.parse(data);
            if (typeof parsed.text === 'string') {
              text = parsed.text;
            }
          } catch {
            // plain text chunk fallback
          }
          onChunk(text);
        } else if (eventType === 'done') {
          isStreamCompleted = true;
          let parsed = {};
          try {
            parsed = JSON.parse(data);
          } catch {
            // ignore
          }
          onDone(parsed);
          return;
        } else if (eventType === 'error') {
          isStreamCompleted = true;
          let code = '';
          try {
            const parsed = JSON.parse(data);
            code = parsed.code ?? '';
          } catch {
            code = data;
          }
          onError({ code });
          return;
        }
      }
    }

    // 스트림이 done이나 error 없이 닫힌 경우
    if (!isStreamCompleted && !signal?.aborted) {
      onError({ message: '답을 끝까지 받지 못했어요' });
    }
  } catch (err: unknown) {
    if (signal?.aborted) return;
    onError({
      message: err instanceof Error ? err.message : '스트리밍 중 오류가 발생했습니다.',
    });
  }
}

import {
  chatMessageApi,
  chatSessionApi,
  type ChatMessageResponse,
  type ChatSessionResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

/**
 * 회원의 활성 상담 세션을 시작하거나 기존 활성 세션을 가져온다.
 */
export async function startSession(signal?: AbortSignal): Promise<ChatSessionResponse> {
  return unwrap(chatSessionApi.startSession({ signal }));
}

/**
 * 진행 중인 상담 세션을 명시적으로 종료(COMPLETED) 처리한다.
 */
export async function completeSession(sessionId: number): Promise<ChatSessionResponse> {
  return unwrap(chatSessionApi.completeSession({ sessionId }));
}

/**
 * 특정 상담 세션의 모든 메시지 목록을 sequence 순으로 조회한다.
 */
export async function getMessages(
  sessionId: number,
  signal?: AbortSignal,
): Promise<ChatMessageResponse[]> {
  return unwrap(chatMessageApi.getMessages({ sessionId }, { signal }));
}

export { streamMessage, type StreamMessageOptions } from './api/streamMessage';

import type { QueryClient } from '@tanstack/react-query';

import { type ChatSessionResponse, ChatSessionResponseStatusEnum } from '@/common/api/clients';
import type { MemberEventListener } from '@/common/sse/useMemberEventStream';

import { useChatStreamingStore } from './model/useChatStreamingStore';
import { chatQueries } from './queries';

// 서버가 보내는 상담 바뀜 신호(event: chat)의 data. 메시지 내용은 없다.
interface ChatSessionSignal {
  sessionId: number;
  status: ChatSessionResponseStatusEnum;
}

const STATUSES: readonly string[] = Object.values(ChatSessionResponseStatusEnum);

function isStatus(value: unknown): value is ChatSessionResponseStatusEnum {
  return typeof value === 'string' && STATUSES.includes(value);
}

export function parseChatSessionSignal(data: string): ChatSessionSignal | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return null;
  }
  if (typeof parsed !== 'object' || parsed === null) return null;
  if (!('sessionId' in parsed) || !('status' in parsed)) return null;
  const { sessionId, status } = parsed;
  if (typeof sessionId !== 'number' || !isStatus(status)) return null;
  return { sessionId, status };
}

// 다른 창(브라우저·기기)에서 상담이 바뀌면 상담 내역과 그 상담의 대화를 다시 받는다.
// 진행 중 상담 쿼리는 POST(상담 시작)라 무효화하면 새 상담이 생길 수 있어 그냥 무효화하지 않는다.
export function handleChatSessionSignal(queryClient: QueryClient, data: string) {
  const signal = parseChatSessionSignal(data);
  if (!signal) return;

  void queryClient.invalidateQueries({ queryKey: chatQueries.sessions() });

  // 이 창이 그 상담의 답을 받는 중이면 건너뛴다. 받는 중인 말풍선과 다시 받은 기록이 겹쳐 두 번 보이지 않게 한다.
  // 이 창은 답이 끝나면 스스로 기록을 다시 받는다.
  if (useChatStreamingStore.getState().streamingSessionId !== signal.sessionId) {
    void queryClient.invalidateQueries({ queryKey: chatQueries.messages(signal.sessionId) });
  }

  const active = queryClient.getQueryData<ChatSessionResponse>(chatQueries.activeSession());
  if (!active) return;

  if (signal.status !== 'ACTIVE') {
    // 진행 중이던 상담이 다른 창에서 끝났다. 상태만 바꿔 채팅창·상담 내역을 끝난 상담으로 보여 준다(규칙 8.2 예외).
    if (active.id === signal.sessionId && active.status !== signal.status) {
      queryClient.setQueryData<ChatSessionResponse>(chatQueries.activeSession(), {
        ...active,
        status: signal.status,
      });
    }
    return;
  }

  // 다른 창에서 새 상담을 시작했다. 서버에 진행 중 상담이 이미 있어 다시 받으면(POST) 새로 만들지 않고 그 상담을 돌려준다.
  if (active.id !== signal.sessionId) {
    void queryClient.invalidateQueries({ queryKey: chatQueries.activeSession() });
  }
}

// 다시 연결되면 상담 내역과 열려 있는 대화를 다시 받는다. 진행 중 상담은 위와 같은 이유로 건드리지 않는다.
function refreshChatSessions(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: chatQueries.sessions() });
  // 지금 열려 있는 대화(채팅창·상담 내역 오른쪽)도 놓친 메시지를 메운다. 답을 받는 중인 상담은 위와 같은 이유로 건너뛴다.
  const { streamingSessionId } = useChatStreamingStore.getState();
  void queryClient.invalidateQueries({
    queryKey: [...chatQueries.all(), 'messages'],
    type: 'active',
    predicate: (query) => query.queryKey[2] !== streamingSessionId,
  });
}

export const chatSessionEventListener: MemberEventListener = {
  event: 'chat',
  onMessage: handleChatSessionSignal,
  onReconnect: refreshChatSessions,
};

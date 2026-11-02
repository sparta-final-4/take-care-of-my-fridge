import { create } from 'zustand';

interface ChatStreamingState {
  // 이 창에서 지금 답을 받는 중인 상담. 채팅창·마이페이지 오른쪽 칸 중 한 곳만 열리므로 하나만 둔다.
  streamingSessionId: number | null;
  startStreaming: (sessionId: number) => void;
  // 늦게 끝난 이전 상담이 새로 시작한 상담 기록을 지우지 않게, 같은 상담일 때만 비운다.
  finishStreaming: (sessionId: number) => void;
}

export const useChatStreamingStore = create<ChatStreamingState>((set) => ({
  streamingSessionId: null,
  startStreaming: (sessionId) => set({ streamingSessionId: sessionId }),
  finishStreaming: (sessionId) =>
    set((state) => (state.streamingSessionId === sessionId ? { streamingSessionId: null } : state)),
}));

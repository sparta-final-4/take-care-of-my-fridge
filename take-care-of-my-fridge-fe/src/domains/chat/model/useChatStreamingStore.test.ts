import { afterEach, describe, expect, it } from 'vitest';

import { useChatStreamingStore } from './useChatStreamingStore';

describe('useChatStreamingStore', () => {
  afterEach(() => {
    useChatStreamingStore.setState({ streamingSessionId: null });
  });

  it('답 받기를 시작하면 상담 id를 들고, 끝나면 비운다', () => {
    useChatStreamingStore.getState().startStreaming(7);
    expect(useChatStreamingStore.getState().streamingSessionId).toBe(7);

    useChatStreamingStore.getState().finishStreaming(7);
    expect(useChatStreamingStore.getState().streamingSessionId).toBeNull();
  });

  it('다른 상담의 끝 기록은 지금 받는 상담을 지우지 않는다', () => {
    useChatStreamingStore.getState().startStreaming(8);

    useChatStreamingStore.getState().finishStreaming(7);
    expect(useChatStreamingStore.getState().streamingSessionId).toBe(8);
  });
});

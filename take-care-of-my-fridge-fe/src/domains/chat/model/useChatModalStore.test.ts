import { afterEach, describe, expect, it, vi } from 'vitest';

import { useChatModalStore } from './useChatModalStore';

describe('useChatModalStore', () => {
  afterEach(() => {
    useChatModalStore.setState({ isOpen: false, inlineHost: null });
  });

  it('화면 안 대화가 없으면 openChat이 채팅창을 연다', () => {
    useChatModalStore.getState().openChat();
    expect(useChatModalStore.getState().isOpen).toBe(true);
  });

  it('화면 안 대화를 등록하면 열린 채팅창을 닫고, openChat은 그 화면으로 넘긴다', () => {
    const host = vi.fn();
    useChatModalStore.setState({ isOpen: true });

    useChatModalStore.getState().setInlineHost(host);
    expect(useChatModalStore.getState().isOpen).toBe(false);

    useChatModalStore.getState().openChat();
    expect(host).toHaveBeenCalledTimes(1);
    expect(useChatModalStore.getState().isOpen).toBe(false);
  });

  it('등록을 풀면 다시 채팅창이 열린다', () => {
    useChatModalStore.getState().setInlineHost(vi.fn());
    useChatModalStore.getState().setInlineHost(null);

    useChatModalStore.getState().openChat();
    expect(useChatModalStore.getState().isOpen).toBe(true);
  });
});

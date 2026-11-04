import { afterEach, describe, expect, it, vi } from 'vitest';

import { useChatModalStore } from './useChatModalStore';

describe('useChatModalStore', () => {
  afterEach(() => {
    useChatModalStore.setState({ isOpen: false, inlineHost: null, selectedIngredients: [] });
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

  it('선택 재료를 주면 담고, 주지 않으면 이전 선택을 비운 채 연다', () => {
    useChatModalStore.getState().openChat([{ ingredientId: 1, name: '배추' }]);
    expect(useChatModalStore.getState().selectedIngredients).toEqual([
      { ingredientId: 1, name: '배추' },
    ]);

    useChatModalStore.getState().closeChat();
    useChatModalStore.getState().openChat();
    expect(useChatModalStore.getState().selectedIngredients).toEqual([]);
  });

  it('화면 안 대화로 넘길 때도 선택 재료를 유지한다', () => {
    const host = vi.fn(() => {
      expect(useChatModalStore.getState().selectedIngredients).toHaveLength(2);
    });
    useChatModalStore.getState().setInlineHost(host);

    useChatModalStore.getState().openChat([
      { ingredientId: 1, name: '배추' },
      { ingredientId: 2, name: '버섯' },
    ]);

    expect(host).toHaveBeenCalledTimes(1);
    expect(useChatModalStore.getState().selectedIngredients).toHaveLength(2);
  });

  it('선택 재료를 하나씩 빼거나 모두 비운다', () => {
    useChatModalStore.getState().openChat([
      { ingredientId: 1, name: '배추' },
      { ingredientId: 2, name: '버섯' },
    ]);

    useChatModalStore.getState().removeSelectedIngredient(1);
    expect(useChatModalStore.getState().selectedIngredients).toEqual([
      { ingredientId: 2, name: '버섯' },
    ]);

    useChatModalStore.getState().clearSelectedIngredients();
    expect(useChatModalStore.getState().selectedIngredients).toEqual([]);
  });
});

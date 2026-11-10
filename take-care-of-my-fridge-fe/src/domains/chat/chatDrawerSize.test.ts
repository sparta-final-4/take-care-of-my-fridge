import { afterEach, describe, expect, it, vi } from 'vitest';

import { readChatDrawerWide, saveChatDrawerWide } from './chatDrawerSize';

function stubStorage() {
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => store.set(key, value),
    removeItem: (key: string) => store.delete(key),
  });
}

describe('상담 서랍 크게 보기 기억', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('저장한 값을 다시 읽는다', () => {
    stubStorage();
    expect(readChatDrawerWide()).toBe(false);
    saveChatDrawerWide(true);
    expect(readChatDrawerWide()).toBe(true);
    saveChatDrawerWide(false);
    expect(readChatDrawerWide()).toBe(false);
  });

  it('저장소를 못 쓰면 기본 크기', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
    });
    expect(readChatDrawerWide()).toBe(false);
    expect(() => saveChatDrawerWide(true)).not.toThrow();
  });
});

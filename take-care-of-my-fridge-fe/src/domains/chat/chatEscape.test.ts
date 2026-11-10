import { describe, expect, it } from 'vitest';

import { shouldCloseChatOnEscape } from './chatEscape';

const base = { key: 'Escape', isComposing: false, keyCode: 27, overlayOpen: false };

describe('shouldCloseChatOnEscape', () => {
  it('ESC면 닫는다', () => {
    expect(shouldCloseChatOnEscape(base)).toBe(true);
  });

  it('다른 키는 무시', () => {
    expect(shouldCloseChatOnEscape({ ...base, key: 'Enter', keyCode: 13 })).toBe(false);
  });

  it('한글 조합 중이면 조합 취소만', () => {
    expect(shouldCloseChatOnEscape({ ...base, isComposing: true })).toBe(false);
    expect(shouldCloseChatOnEscape({ ...base, keyCode: 229 })).toBe(false);
  });

  it('위에 다른 서랍·모달·드롭다운이 열려 있으면 그것만 닫히게 둔다', () => {
    expect(shouldCloseChatOnEscape({ ...base, overlayOpen: true })).toBe(false);
  });
});

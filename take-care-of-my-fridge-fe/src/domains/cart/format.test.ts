import { describe, expect, it } from 'vitest';

import { formatRemaining } from './format';

describe('formatRemaining', () => {
  it('하루가 안 남았으면 시:분:초로 쓴다', () => {
    expect(formatRemaining(3_723_000)).toBe('01:02:03');
  });

  it('하루가 넘게 남았으면 일 수를 앞에 붙인다', () => {
    expect(formatRemaining(90_061_000)).toBe('1일 01:01:01');
  });

  it('초 미만은 버리고 음수는 0으로 쓴다', () => {
    expect(formatRemaining(999)).toBe('00:00:00');
    expect(formatRemaining(-5000)).toBe('00:00:00');
  });
});

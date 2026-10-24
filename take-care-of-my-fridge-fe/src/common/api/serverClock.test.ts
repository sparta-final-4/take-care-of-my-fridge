import { afterEach, describe, expect, it, vi } from 'vitest';

import { serverNow, syncServerClock } from './serverClock';

describe('serverClock', () => {
  afterEach(() => {
    vi.useRealTimers();
    syncServerClock(new Date(0).toUTCString(), 0);
  });

  it('서버가 기기보다 빠르면 그만큼 더한 시각을 돌려준다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-23T10:00:00Z'));

    syncServerClock('Fri, 23 Oct 2026 10:05:00 GMT');

    expect(serverNow()).toBe(new Date('2026-10-23T10:05:00Z').getTime());
  });

  it('시간이 흘러도 차이를 유지한다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-23T10:00:00Z'));
    syncServerClock('Fri, 23 Oct 2026 09:58:00 GMT');

    vi.advanceTimersByTime(30_000);

    expect(serverNow()).toBe(new Date('2026-10-23T09:58:30Z').getTime());
  });

  it('Date 헤더가 없거나 읽을 수 없으면 이전 차이를 그대로 둔다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-23T10:00:00Z'));
    syncServerClock('Fri, 23 Oct 2026 10:05:00 GMT');

    syncServerClock(undefined);
    syncServerClock('not a date');

    expect(serverNow()).toBe(new Date('2026-10-23T10:05:00Z').getTime());
  });
});

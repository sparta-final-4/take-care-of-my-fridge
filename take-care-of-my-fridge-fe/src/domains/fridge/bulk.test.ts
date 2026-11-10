import { describe, expect, it } from 'vitest';

import { runInChunks } from './bulk';

describe('runInChunks', () => {
  it('정해진 수보다 많이 동시에 보내지 않는다', async () => {
    let running = 0;
    let maxRunning = 0;
    await runInChunks([1, 2, 3, 4, 5, 6, 7], async () => {
      running += 1;
      maxRunning = Math.max(maxRunning, running);
      await new Promise((resolve) => setTimeout(resolve, 1));
      running -= 1;
    });
    expect(maxRunning).toBe(3);
  });

  it('일부가 실패해도 끝까지 보내고 성공·실패를 나눠 돌려준다', async () => {
    const sent: number[] = [];
    const result = await runInChunks([1, 2, 3, 4, 5], async (id) => {
      sent.push(id);
      if (id % 2 === 0) throw new Error('실패');
    });
    expect(sent).toEqual([1, 2, 3, 4, 5]);
    expect(result).toEqual({ succeededIds: [1, 3, 5], failedIds: [2, 4] });
  });
});

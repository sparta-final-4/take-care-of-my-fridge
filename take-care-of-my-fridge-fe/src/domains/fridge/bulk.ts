// 여러 재료를 한 번에 처리할 때 서버에 동시에 너무 많이 보내지 않도록 몇 개씩 나눠 차례로 보낸다.
export const BULK_CONCURRENCY = 3;

export interface BulkResult {
  succeededIds: number[];
  failedIds: number[];
}

// 하나가 실패해도 나머지는 계속 보내고, 성공·실패한 id를 나눠 돌려준다.
export async function runInChunks(
  ids: number[],
  run: (id: number) => Promise<unknown>,
  size = BULK_CONCURRENCY,
): Promise<BulkResult> {
  const result: BulkResult = { succeededIds: [], failedIds: [] };
  for (let start = 0; start < ids.length; start += size) {
    const chunk = ids.slice(start, start + size);
    const settled = await Promise.allSettled(chunk.map(run));
    settled.forEach((outcome, index) => {
      (outcome.status === 'fulfilled' ? result.succeededIds : result.failedIds).push(chunk[index]);
    });
  }
  return result;
}

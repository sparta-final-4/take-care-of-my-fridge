import type { AxiosAdapter } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { axiosInstance } from '@/common/api/axiosInstance';

import { getAuditLogs, getMembers } from './api';

const page = {
  content: [],
  pageNumber: 1,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
  isLast: true,
};

describe('관리자 조회 API', () => {
  const adapter = vi.fn<AxiosAdapter>();
  const originalAdapter = axiosInstance.defaults.adapter;

  beforeEach(() => {
    adapter.mockImplementation(async (config) => ({
      data: { success: true, data: page, error: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }));
    axiosInstance.defaults.adapter = adapter;
  });

  afterEach(() => {
    adapter.mockReset();
    axiosInstance.defaults.adapter = originalAdapter;
  });

  it('회원 목록 조건을 쿼리로 보내고 빈 조건은 뺀다', async () => {
    await expect(
      getMembers({ keyword: 'cook', role: undefined, page: 2, size: 20 }),
    ).resolves.toEqual(page);

    expect(adapter.mock.calls[0][0].url).toBe('/api/admin/members?keyword=cook&page=2&size=20');
  });

  it('작업 이력 조건을 쿼리로 보낸다', async () => {
    await getAuditLogs({ action: 'DEAL_ANALYSIS', result: 'FAILED', page: 1, size: 20 });

    expect(adapter.mock.calls[0][0].url).toBe(
      '/api/admin/audit-logs?action=DEAL_ANALYSIS&result=FAILED&page=1&size=20',
    );
  });
});

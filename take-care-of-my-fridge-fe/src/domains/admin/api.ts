import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse, PageResponse } from '@/common/api/types';
import { csrfHeaders } from '@/domains/auth/api';
import type { MyInfoResponse } from '@/domains/member/api';

export interface MemberListRequest {
  keyword?: string;
  role?: MyInfoResponse['role'];
  page: number;
  size: number;
}

export interface MemberSummaryResponse {
  memberId: number;
  email: string;
  nickname: string;
  role: MyInfoResponse['role'];
  status: MyInfoResponse['status'];
  createdAt: string;
  promotedAt: string | null;
  promotedBy: string | null;
}

export async function getMembers(request: MemberListRequest, signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<PageResponse<MemberSummaryResponse>>>(
    '/api/admin/members',
    { params: request, signal },
  );
  return data.data;
}

export async function promoteMember(memberId: number) {
  await axiosInstance.post(
    `/api/admin/members/${encodeURIComponent(memberId)}/promotion`,
    undefined,
    {
      headers: await csrfHeaders(),
    },
  );
}

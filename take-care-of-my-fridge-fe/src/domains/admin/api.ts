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

export interface AuditLogSearchRequest {
  from?: string;
  to?: string;
  action?: AuditLogResponse['action'];
  result?: AuditLogResponse['result'];
  targetType?: string;
  targetId?: number;
  page: number;
  size: number;
}

export interface AuditLogResponse {
  auditLogId: number;
  createdAt: string;
  actorEmail: string;
  actorRole: MyInfoResponse['role'];
  action:
    | 'PRODUCT_CREATE'
    | 'PRODUCT_DELETE'
    | 'PRICE_UPDATE'
    | 'DEAL_REVIEW'
    | 'EVENT_REQUEST'
    | 'ROLE_CHANGE';
  targetType: string;
  targetId: number | null;
  result: 'SUCCESS' | 'FAILED' | 'DENIED';
  beforeValue: Record<string, string> | null;
  afterValue: Record<string, string> | null;
}

export async function getAuditLogs(request: AuditLogSearchRequest, signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<PageResponse<AuditLogResponse>>>(
    '/api/admin/audit-logs',
    { params: request, signal },
  );
  return data.data;
}

import {
  adminAuditApi,
  adminMemberApi,
  type AuditLogSearchRequest,
  type MemberListRequest,
  type PageResponseAuditLogResponse,
  type PageResponseMemberSummaryResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getMembers(
  request: MemberListRequest,
  signal?: AbortSignal,
): Promise<PageResponseMemberSummaryResponse> {
  return unwrap(adminMemberApi.getMembers({ request }, { signal }));
}

export async function promoteMember(memberId: number): Promise<void> {
  await unwrap(adminMemberApi.promote({ memberId }));
}

export async function getAuditLogs(
  request: AuditLogSearchRequest,
  signal?: AbortSignal,
): Promise<PageResponseAuditLogResponse> {
  return unwrap(adminAuditApi.getAuditLogs({ request }, { signal }));
}

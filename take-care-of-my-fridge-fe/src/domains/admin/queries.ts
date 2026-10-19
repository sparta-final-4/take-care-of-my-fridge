import { queryOptions } from '@tanstack/react-query';

import {
  getAuditLogs,
  getMembers,
  type AuditLogSearchRequest,
  type MemberListRequest,
} from './api';

export const adminQueries = {
  all: ['admin'] as const,
  memberLists: () => [...adminQueries.all, 'members'] as const,
  members: (request: MemberListRequest) =>
    queryOptions({
      queryKey: [...adminQueries.memberLists(), request],
      queryFn: ({ signal }) => getMembers(request, signal),
    }),
  auditLogs: (request: AuditLogSearchRequest) =>
    queryOptions({
      queryKey: [...adminQueries.all, 'audit-logs', request],
      queryFn: ({ signal }) => getAuditLogs(request, signal),
    }),
};

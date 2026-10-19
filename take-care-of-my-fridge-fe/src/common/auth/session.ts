import { queryClient } from '@/app/queryClient';
import type { MyInfoResponse } from '@/domains/member/api';

import { useAuthStore } from './authStore';
import { nextSessionId, sessionId } from './sessionId';

export async function setSession(member: MyInfoResponse | null) {
  const id = nextSessionId();
  await queryClient.cancelQueries();
  if (id !== sessionId()) return;
  queryClient.clear();
  useAuthStore.setState({ status: member ? 'authenticated' : 'anonymous', member });
}

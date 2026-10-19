import { isAxiosError } from 'axios';

import { refresh } from '@/domains/auth/api';
import { getMyInfo } from '@/domains/member/api';

import { useAuthStore } from './authStore';
import { setSession } from './session';
import { sessionId } from './sessionId';

let pending: Promise<void> | undefined;

export function initSession() {
  if (pending) return pending;
  const id = sessionId();
  useAuthStore.setState({ status: 'initializing' });
  pending = (async () => {
    try {
      let member;
      try {
        member = await getMyInfo();
      } catch (error) {
        if (!isAxiosError(error) || error.response?.status !== 401) throw error;
        await refresh();
        member = await getMyInfo();
      }
      if (id === sessionId()) await setSession(member);
    } catch (error) {
      if (id !== sessionId()) return;
      if (isAxiosError(error) && error.response?.status === 401) {
        await setSession(null);
      } else {
        useAuthStore.setState({ status: 'error', member: null });
      }
    }
  })().finally(() => {
    pending = undefined;
  });
  return pending;
}

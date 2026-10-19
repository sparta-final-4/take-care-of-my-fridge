import { useEffect } from 'react';

import { logout } from '@/domains/auth/api';

import { useAuthStore } from './authStore';
import { setSession } from './session';
import { sessionId } from './sessionId';

const IDLE_MS = 30 * 60 * 1000;

export function IdleLogout() {
  const status = useAuthStore((state) => state.status);

  useEffect(() => {
    if (status !== 'authenticated') return;
    let timer: ReturnType<typeof setTimeout>;
    const reset = () => {
      clearTimeout(timer);
      timer = setTimeout(() => {
        const id = sessionId();
        void logout()
          .catch(() => undefined)
          .finally(() => {
            if (id === sessionId()) void setSession(null);
          });
      }, IDLE_MS);
    };
    reset();
    window.addEventListener('pointerdown', reset);
    window.addEventListener('keydown', reset);
    return () => {
      clearTimeout(timer);
      window.removeEventListener('pointerdown', reset);
      window.removeEventListener('keydown', reset);
    };
  }, [status]);

  return null;
}

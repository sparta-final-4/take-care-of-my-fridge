import { isAxiosError, type InternalAxiosRequestConfig } from 'axios';

import { axiosInstance } from '@/common/api/axiosInstance';
import { refresh } from '@/domains/auth/api';

import { useAuthStore } from './authStore';
import { setSession } from './session';
import { sessionId } from './sessionId';

type SessionRequest = InternalAxiosRequestConfig & { sessionId?: number; retried?: boolean };

let refreshing: Promise<void> | undefined;

axiosInstance.interceptors.request.use((config: SessionRequest) => {
  config.sessionId = sessionId();
  return config;
});

axiosInstance.interceptors.response.use(undefined, async (error: unknown) => {
  if (!isAxiosError(error) || error.response?.status !== 401) return Promise.reject(error);
  const request = error.config as SessionRequest | undefined;
  if (!request || request.sessionId !== sessionId()) return Promise.reject(error);
  if (useAuthStore.getState().status !== 'authenticated') return Promise.reject(error);
  if (/\/api\/(auth|members\/me)/.test(request.url ?? '')) return Promise.reject(error);

  if (!request.retried) {
    request.retried = true;
    try {
      refreshing ??= refresh().finally(() => {
        refreshing = undefined;
      });
      await refreshing;
      if (request.sessionId === sessionId()) return axiosInstance(request);
    } catch {
      // 재발급 실패는 아래에서 현재 세션을 종료한다.
    }
  }
  if (request.sessionId === sessionId()) await setSession(null);
  return Promise.reject(error);
});

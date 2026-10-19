import { isAxiosError, type InternalAxiosRequestConfig } from 'axios';

import { axiosInstance } from '@/common/api/axiosInstance';
import { getErrorCode } from '@/common/api/errors';
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
  if ((request.url ?? '').startsWith('/api/auth/')) return Promise.reject(error);

  if (getErrorCode(error) === 'A006') {
    await setSession(null, 'suspended');
    return Promise.reject(error);
  }

  let reason: 'expired' | 'suspended' = 'expired';
  if (!request.retried) {
    request.retried = true;
    try {
      refreshing ??= refresh().finally(() => {
        refreshing = undefined;
      });
      await refreshing;
      if (request.sessionId === sessionId()) return axiosInstance(request);
    } catch (refreshError) {
      if (getErrorCode(refreshError) === 'A006') {
        reason = 'suspended';
      }
    }
  }
  if (request.sessionId === sessionId()) await setSession(null, reason);
  return Promise.reject(error);
});

import axios, { isAxiosError, type InternalAxiosRequestConfig } from 'axios';

import { getErrorCode } from './errors';

// 모든 API 호출이 쓰는 axios 인스턴스. 생성된 API 클래스에도 이 인스턴스를 넘긴다.
// baseURL은 서버 주소(origin)만 갖고, /api prefix는 생성된 API 경로에 들어 있다.
// 인증은 HttpOnly 쿠키 기반이라 토큰 헤더를 붙이지 않는다.
export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10_000,
  withCredentials: true,
});

// CSRF: 백엔드(csrf.spa())는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 받는다.
// API와 같은 출처(로컬 Vite proxy, 운영 CloudFront /api/*)라 axios가 쿠키를 읽어 헤더를 자동으로 붙인다.
// 여기서는 쿠키가 없으면 먼저 발급받고, 403 AUTH_002(토큰 불일치)면 다시 받아 한 번만 재시도한다.
// 토큰 발급 요청은 인스턴스 자체에 필요해 api.ts가 아닌 여기서 직접 호출한다.
const SAFE_METHODS = ['get', 'head', 'options'];
type CsrfRequest = InternalAxiosRequestConfig & { csrfRetried?: boolean };

let issuing: Promise<unknown> | undefined;

function issueCsrfCookie() {
  issuing ??= axiosInstance.get('/api/auth/csrf').finally(() => {
    issuing = undefined;
  });
  return issuing;
}

function hasCsrfCookie() {
  return document.cookie.split('; ').some((cookie) => cookie.startsWith('XSRF-TOKEN='));
}

axiosInstance.interceptors.request.use(async (config) => {
  if (!SAFE_METHODS.includes(config.method ?? 'get') && !hasCsrfCookie()) await issueCsrfCookie();
  return config;
});

axiosInstance.interceptors.response.use(undefined, async (error: unknown) => {
  if (!isAxiosError(error) || error.response?.status !== 403) return Promise.reject(error);
  const request = error.config as CsrfRequest | undefined;
  if (!request || request.csrfRetried || getErrorCode(error) !== 'AUTH_002') {
    return Promise.reject(error);
  }
  request.csrfRetried = true;
  await issueCsrfCookie();
  return axiosInstance(request);
});

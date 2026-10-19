import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';
import type { MyInfoResponse } from '@/domains/member/api';

export interface LoginRequest {
  email: string;
  password: string;
}

export async function login(request: LoginRequest) {
  const { data } = await axiosInstance.post<ApiResponse<MyInfoResponse>>(
    '/api/auth/login',
    request,
  );
  return data.data;
}

// CsrfController.CsrfResponse
export interface CsrfResponse {
  headerName: string;
  token: string;
}

const CSRF_COOKIE = 'XSRF-TOKEN';

function readCookie(name: string) {
  const prefix = `${name}=`;
  const cookie = document.cookie.split('; ').find((item) => item.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : undefined;
}

// 백엔드 csrf.spa()는 헤더 값을 XSRF-TOKEN 쿠키의 원본 토큰과 비교한다.
// 응답 body의 token은 마스킹된 값이라, 쿠키를 읽을 수 없을 때만 쓴다.
export async function csrfHeaders() {
  const { data } = await axiosInstance.get<ApiResponse<CsrfResponse>>('/api/auth/csrf');
  return { [data.data.headerName]: readCookie(CSRF_COOKIE) ?? data.data.token };
}

export async function refresh() {
  await axiosInstance.post('/api/auth/refresh', undefined, { headers: await csrfHeaders() });
}

export async function logout() {
  await axiosInstance.post('/api/auth/logout', undefined, { headers: await csrfHeaders() });
}

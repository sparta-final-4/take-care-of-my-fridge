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

export async function refresh() {
  await axiosInstance.post('/api/auth/refresh');
}

export async function logout() {
  await axiosInstance.post('/api/auth/logout');
}

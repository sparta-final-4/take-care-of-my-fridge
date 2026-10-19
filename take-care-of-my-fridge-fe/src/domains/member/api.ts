import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';

// MyInfoResponse.java와 필드·enum을 맞춘다.
export interface MyInfoResponse {
  memberId: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED';
  tastes: Array<'SALTY' | 'SWEET' | 'SPICY' | 'UMAMI' | 'MILD'>;
  createdAt: string;
}

export async function getMyInfo(signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<MyInfoResponse>>('/api/members/me', {
    signal,
  });
  return data.data;
}

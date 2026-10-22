import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';
import { csrfHeaders } from '@/domains/auth/api';

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

export interface SignUpRequest {
  email: string;
  password: string;
  nickname: string;
  phone: string;
  tastes: MyInfoResponse['tastes'];
}

export interface SignUpResponse {
  memberId: number;
}

// EmailAvailabilityResponse.java, NicknameAvailabilityResponse.java
export interface EmailAvailabilityResponse {
  available: boolean;
}

export interface NicknameAvailabilityResponse {
  available: boolean;
}

export async function checkEmailAvailability(email: string, signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<EmailAvailabilityResponse>>(
    '/api/members/email-availability',
    { params: { email }, signal },
  );
  return data.data.available;
}

export async function checkNicknameAvailability(nickname: string, signal?: AbortSignal) {
  const { data } = await axiosInstance.get<ApiResponse<NicknameAvailabilityResponse>>(
    '/api/members/nickname-availability',
    { params: { nickname }, signal },
  );
  return data.data.available;
}

export async function signUp(request: SignUpRequest) {
  const { data } = await axiosInstance.post<ApiResponse<SignUpResponse>>('/api/members', request);
  return data.data;
}

export async function changeNickname(nickname: string) {
  await axiosInstance.patch(
    '/api/members/me/nickname',
    { nickname },
    { headers: await csrfHeaders() },
  );
}

export async function changeTastes(tastes: MyInfoResponse['tastes']) {
  await axiosInstance.put('/api/members/me/tastes', { tastes }, { headers: await csrfHeaders() });
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export async function changePassword(request: ChangePasswordRequest) {
  await axiosInstance.patch('/api/members/me/password', request, { headers: await csrfHeaders() });
}

import { authApi, type LoginRequest, type MyInfoResponse } from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function login(request: LoginRequest): Promise<MyInfoResponse> {
  return unwrap(authApi.login({ loginRequest: request }));
}

export async function refresh(): Promise<void> {
  await unwrap(authApi.refresh());
}

export async function logout(): Promise<void> {
  await unwrap(authApi.logout());
}

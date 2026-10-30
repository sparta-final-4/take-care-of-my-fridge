import {
  memberApi,
  type ChangePasswordRequest,
  type ChangeTastesRequest,
  type MyInfoResponse,
  type SignUpRequest,
  type SignUpResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getMyInfo(signal?: AbortSignal): Promise<MyInfoResponse> {
  return unwrap(memberApi.getMyInfo({ signal }));
}

export async function checkEmailAvailability(email: string, signal?: AbortSignal) {
  const { available } = await unwrap(
    memberApi.checkEmailAvailability({ request: { email } }, { signal }),
  );
  return available ?? false;
}

export async function checkNicknameAvailability(nickname: string, signal?: AbortSignal) {
  const { available } = await unwrap(
    memberApi.checkNicknameAvailability({ request: { nickname } }, { signal }),
  );
  return available ?? false;
}

export async function signUp(request: SignUpRequest): Promise<SignUpResponse> {
  return unwrap(memberApi.signUp({ signUpRequest: request }));
}

export async function changeNickname(nickname: string): Promise<void> {
  await unwrap(memberApi.changeNickname({ changeNicknameRequest: { nickname } }));
}

export async function changeTastes(tastes: ChangeTastesRequest['tastes']): Promise<void> {
  await unwrap(memberApi.changeTastes({ changeTastesRequest: { tastes } }));
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await unwrap(memberApi.changePassword({ changePasswordRequest: request }));
}

import { isAxiosError } from 'axios';

// 에러 분류와 메시지 변환은 이 파일에서만 한다.
const DEFAULT_MESSAGE = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';

export function getErrorCode(error: unknown): string | undefined {
  if (!isAxiosError(error)) return undefined;
  const data: unknown = error.response?.data;
  if (typeof data !== 'object' || data === null || !('error' in data)) return undefined;
  const responseError = data.error;
  if (typeof responseError !== 'object' || responseError === null || !('code' in responseError))
    return undefined;
  return typeof responseError.code === 'string' ? responseError.code : undefined;
}

// 응답이 없거나 5xx면 서버가 요청을 처리했는지 알 수 없다. 같은 요청을 다시 보낼 때 판단에 쓴다.
export function isOutcomeUnknown(error: unknown) {
  if (!isAxiosError(error)) return false;
  return !error.response || error.response.status >= 500;
}

// 백엔드 공통 응답(ApiResponse.java): { success, data, error }.
function getServerMessage(data: unknown) {
  if (typeof data !== 'object' || data === null || !('error' in data)) return undefined;
  const { error } = data;
  if (typeof error !== 'object' || error === null || !('message' in error)) return undefined;
  return typeof error.message === 'string' && error.message ? error.message : undefined;
}

// 401은 인터셉터가 세션 만료로 처리하므로 화면에서 따로 알리지 않는다.
export function isUnauthorized(error: unknown) {
  return isAxiosError(error) && error.response?.status === 401;
}

export function isNotFound(error: unknown) {
  return isAxiosError(error) && error.response?.status === 404;
}

export function getErrorMessage(error: unknown) {
  if (!isAxiosError(error)) return DEFAULT_MESSAGE;

  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return '응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (!error.response) return '네트워크 연결을 확인해 주세요.';

  const { status, data } = error.response;
  if (status >= 500) return '서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.';
  if (status === 403) return '이 작업을 할 권한이 없습니다.';

  return getServerMessage(data) ?? DEFAULT_MESSAGE;
}

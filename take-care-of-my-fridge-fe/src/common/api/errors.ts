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

// 상품 이미지 업로드. S3 PUT(fetch) 실패는 axios 오류가 아니라 일반 Error로 온다.
export function getImageUploadErrorMessage(error: unknown) {
  // IMAGE_001(503): 버킷 설정이 없는 환경(로컬). 5xx 공통 문구 대신 URL 직접 입력을 안내한다.
  if (getErrorCode(error) === 'IMAGE_001') {
    return '이 환경에서는 이미지 업로드를 쓸 수 없습니다. 이미지 URL을 직접 입력해 주세요.';
  }
  if (isAxiosError(error)) return getErrorMessage(error);
  return '이미지를 올리지 못했습니다. 잠시 후 다시 시도해 주세요.';
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

// AI 상담(채팅) 오류. 스트리밍 응답은 axios 오류가 아니라 서버가 보낸 오류 코드만 오므로 코드로 문구와 화면 동작을 정한다.
export interface ChatErrorInfo {
  message: string;
  action?: 'restart' | 'blockInput' | 'restoreInput';
}

export function getChatErrorInfo(code?: string): ChatErrorInfo {
  switch (code) {
    case 'CHAT_006':
      return {
        message: '30분 동안 대화가 없어 상담이 끝났어요',
        action: 'restart',
      };
    case 'CHAT_005':
      return {
        message: '끝난 상담이에요',
        action: 'restart',
      };
    case 'CHAT_007':
      return {
        message: '오늘 상담 횟수를 다 썼어요. 내일 다시 이용해 주세요',
        action: 'blockInput',
      };
    case 'CHAT_008':
      return {
        message: '지금은 상담을 이용할 수 없어요. 잠시 후 다시 시도해 주세요',
      };
    case 'A003':
      return {
        message: '로그인이 만료되었어요. 다시 로그인해 주세요',
        action: 'restoreInput',
      };
    default:
      return {
        message: '답을 받지 못했어요. 다시 보내 주세요',
        action: 'restoreInput',
      };
  }
}

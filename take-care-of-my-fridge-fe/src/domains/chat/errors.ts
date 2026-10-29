export interface ChatErrorInfo {
  message: string;
  action?: 'restart' | 'blockInput' | 'restoreInput';
}

export function getChatErrorInfo(code?: string): ChatErrorInfo {
  switch (code) {
    case 'CHAT_006':
    case 'CHAT_SESSION_EXPIRED':
      return {
        message: '30분 동안 대화가 없어 상담이 끝났어요',
        action: 'restart',
      };
    case 'CHAT_005':
    case 'CHAT_ROOM_ALREADY_COMPLETED':
      return {
        message: '끝난 상담이에요',
        action: 'restart',
      };
    case 'CHAT_007':
    case 'CHAT_DAILY_LIMIT_EXCEEDED':
      return {
        message: '오늘 상담 횟수를 다 썼어요. 내일 다시 이용해 주세요',
        action: 'blockInput',
      };
    case 'CHAT_008':
    case 'CHAT_SERVICE_UNAVAILABLE':
      return {
        message: '지금은 상담을 이용할 수 없어요. 잠시 후 다시 시도해 주세요',
      };
    case 'A003':
    case 'UNAUTHORIZED':
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

import dayjs from 'dayjs';

import type { ChatSessionSummaryResponseStatusEnum } from '@/common/api/clients';

export const CHAT_SESSION_STATUS_LABELS: Record<ChatSessionSummaryResponseStatusEnum, string> = {
  ACTIVE: '상담 중',
  COMPLETED: '종료',
  EXPIRED: '만료',
};

// 상담 내역 날짜 표기: 10.28 오후 2:05
export function formatChatDateTime(value?: string): string {
  return value ? dayjs(value).format('MM.DD A h:mm') : '-';
}

// 상담 내역 마지막 대화 시각: 14:05
export function formatChatTime(value?: string): string {
  return value ? dayjs(value).format('HH:mm') : '';
}

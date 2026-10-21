import type { AuditLogResponse } from './api';

export const ACTION_LABELS: Record<AuditLogResponse['action'], string> = {
  PRODUCT_CREATE: '상품 등록',
  PRODUCT_DELETE: '상품 삭제',
  PRICE_UPDATE: '가격 수정',
  DEAL_REVIEW: '할인 후보 검토',
  EVENT_REQUEST: '할인 행사 등록 요청',
  ROLE_CHANGE: '권한 변경',
};

export const RESULT_LABELS: Record<AuditLogResponse['result'], string> = {
  SUCCESS: '성공',
  FAILED: '실패',
  DENIED: '권한 거부',
};

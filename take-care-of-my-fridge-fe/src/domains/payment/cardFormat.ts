import type { BillingKeyResponse } from '@/common/api/clients';

// 회원당 등록할 수 있는 카드 수. 서버(BillingKeyService.MAX_CARDS)와 같은 값이다
export const MAX_CARDS = 5;

export const CARD_TYPE_LABELS: Record<NonNullable<BillingKeyResponse['cardType']>, string> = {
  CREDIT: '신용',
  DEBIT: '체크',
  GIFT: '기프트',
};

// 카드 상품명을 PortOne이 주지 않으면 "등록 카드"로 보인다
export function cardName(card: BillingKeyResponse): string {
  return card.cardName?.trim() || '등록 카드';
}

// 끝 4자리를 알 수 없으면 비워 둔다
export function maskedCardNumber(card: BillingKeyResponse): string {
  return card.cardLast4 ? `···· ${card.cardLast4}` : '';
}

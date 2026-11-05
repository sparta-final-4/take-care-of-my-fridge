// 백엔드 PgProvider. 주문 확인 화면에서 고르는 결제 수단(결제대행사)이고, 정산 화면의 결제 수단 이름에도 쓴다
export type PgProvider = 'KAKAOPAY' | 'TOSSPAY' | 'KICC' | 'INICIS' | 'TOSSPAYMENTS';

// 화면에 보여 줄 순서와 이름
export const PG_PROVIDER_OPTIONS: { value: PgProvider; label: string }[] = [
  { value: 'KAKAOPAY', label: '카카오페이' },
  { value: 'TOSSPAY', label: '토스페이' },
  { value: 'KICC', label: '이지페이' },
  { value: 'INICIS', label: 'KG이니시스' },
  { value: 'TOSSPAYMENTS', label: '토스페이먼츠' },
];

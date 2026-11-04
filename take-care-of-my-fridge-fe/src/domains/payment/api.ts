import { axiosInstance } from '@/common/api/axiosInstance';
import {
  billingKeyApi,
  type BillingKeyPrepareResponse,
  type BillingKeyResponse,
  paymentApi,
  type PaymentConfirmResponse,
} from '@/common/api/clients';
import type { ApiResponse } from '@/common/api/types';
import { unwrap } from '@/common/api/unwrap';

import type { PgProvider } from './pgProvider';

// 백엔드 PaymentPrepareResponse. 생성 클라이언트에 아직 없어 직접 호출한다(oag 재생성 후 생성 코드로 바꾼다).
export interface PaymentPrepareResponse {
  orderId: number;
  portonePaymentId: string;
  orderName: string;
  amount: number;
  expiresAt: string;
  pgProvider: PgProvider;
  // 고른 결제대행사의 결제창 값. 카카오페이·토스페이는 간편결제이고 나머지는 카드 결제창이다
  channelKey: string;
  payMethod: 'CARD' | 'EASY_PAY';
  easyPayProvider: 'KAKAOPAY' | 'TOSSPAY' | null;
}

// 결제하기를 누를 때마다 고른 결제대행사로 새 결제 시도를 만든다.
export async function preparePayment(
  orderId: number,
  pgProvider: PgProvider,
): Promise<PaymentPrepareResponse> {
  const { data } = await axiosInstance.post<ApiResponse<PaymentPrepareResponse>>(
    `/api/orders/${encodeURIComponent(orderId)}/payments`,
    { pgProvider },
  );
  return data.data;
}

// 결제창이 성공으로 끝난 뒤 호출한다. 서버가 PG 결제를 조회해 금액·만료·주문 상태를 확인하고 승인한다.
// 이미 승인된 결제면 같은 결과를 주고, 받지 않는 결제는 PG에서 취소한 뒤 거절 이유를 오류로 준다.
export async function confirmPayment(
  orderId: number,
  portonePaymentId: string,
): Promise<PaymentConfirmResponse> {
  return unwrap(paymentApi.confirm({ paymentConfirmRequest: { orderId, portonePaymentId } }));
}

// 등록 카드 목록. 대표 카드가 먼저, 그다음 최근 등록순이다. 빌링키 값은 오지 않는다.
export async function getCards(signal?: AbortSignal): Promise<BillingKeyResponse[]> {
  return unwrap(billingKeyApi.getCards({ signal }));
}

// 카드 등록 창을 열기 전에 호출한다. 카드가 5장이면 서버가 거절한다(BILLING_002).
export async function prepareCardRegistration(): Promise<BillingKeyPrepareResponse> {
  return unwrap(billingKeyApi.prepare1());
}

// 카드 등록 창에서 받은 빌링키를 넘긴다. 서버가 PortOne에서 다시 확인한 뒤 저장한다.
export async function registerCard(billingKey: string): Promise<BillingKeyResponse> {
  return unwrap(billingKeyApi.register({ billingKeyRegisterRequest: { billingKey } }));
}

// 대표 카드를 바꾸고 바뀐 카드 목록을 받는다.
export async function setDefaultCard(cardId: number): Promise<BillingKeyResponse[]> {
  return unwrap(billingKeyApi.setDefault({ cardId }));
}

export async function deleteCard(cardId: number): Promise<void> {
  await unwrap(billingKeyApi._delete({ cardId }));
}

// 등록 카드로 결제창 없이 결제한다. 금액은 서버가 주문 금액으로 정하고, 결제 확정과 같은 응답을 준다.
// 카드 거절은 PAYMENT_010, 결과 확인 중(결제 대기로 남음)은 PAYMENT_011이다.
export async function payWithCard(
  orderId: number,
  cardId: number,
): Promise<PaymentConfirmResponse> {
  return unwrap(paymentApi.payWithBillingKey({ orderId, billingKeyPaymentRequest: { cardId } }));
}

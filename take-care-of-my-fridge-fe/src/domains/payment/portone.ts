import * as PortOne from '@portone/browser-sdk/v2';

import type { BillingKeyPrepareResponse } from '@/common/api/clients';

import type { PaymentPrepareResponse } from './api';

// 결제창에 넘기는 구매자 정보
export interface PaymentCustomer {
  fullName: string;
  phoneNumber: string;
  email: string;
}

// 서버가 정한 채널과 결제 방식으로 PortOne 결제창을 띄운다. 실패·취소면 응답에 code가 있다.
export async function openPaymentWindow(
  payment: PaymentPrepareResponse,
  customer: PaymentCustomer,
) {
  const request = {
    storeId: import.meta.env.VITE_PORTONE_STORE_ID,
    channelKey: payment.channelKey,
    paymentId: payment.portonePaymentId,
    orderName: payment.orderName,
    totalAmount: payment.amount,
    currency: 'KRW',
    customer,
  } as const;
  if (payment.payMethod === 'EASY_PAY') {
    return PortOne.requestPayment({
      ...request,
      payMethod: 'EASY_PAY',
      easyPay: payment.easyPayProvider ? { easyPayProvider: payment.easyPayProvider } : undefined,
    });
  }
  return PortOne.requestPayment({ ...request, payMethod: 'CARD' });
}

// 서버가 준 정기결제 채널 값으로 PortOne 카드 등록 창을 띄운다. 카드 정보는 결제대행사 창에서만 입력한다.
// 모바일은 창 대신 결제대행사 페이지로 이동했다가 redirectUrl로 돌아오며, 그때는 결과가 주소의 쿼리로 온다.
export async function openCardRegistrationWindow(
  prepared: BillingKeyPrepareResponse,
  redirectUrl: string,
) {
  return PortOne.requestIssueBillingKey({
    storeId: import.meta.env.VITE_PORTONE_STORE_ID,
    channelKey: prepared.channelKey,
    billingKeyMethod: 'CARD',
    issueId: prepared.issueId,
    issueName: prepared.issueName,
    customer: { customerId: prepared.customerId },
    redirectUrl,
  });
}

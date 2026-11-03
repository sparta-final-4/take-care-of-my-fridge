import * as PortOne from '@portone/browser-sdk/v2';

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

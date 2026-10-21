import * as PortOne from '@portone/browser-sdk/v2';

import type { PaymentPrepareResponse } from './api';

// KG이니시스 PC 결제창은 구매자 이름·연락처·이메일이 필수다.
export interface PaymentCustomer {
  fullName: string;
  phoneNumber: string;
  email: string;
}

// PortOne 결제창을 띄운다. 실패·취소면 응답에 code가 있다.
export async function openPaymentWindow(
  payment: PaymentPrepareResponse,
  customer: PaymentCustomer,
) {
  return PortOne.requestPayment({
    storeId: import.meta.env.VITE_PORTONE_STORE_ID,
    channelKey: import.meta.env.VITE_PORTONE_CHANNEL_KEY,
    paymentId: payment.portonePaymentId,
    orderName: payment.orderName,
    totalAmount: payment.amount,
    currency: 'KRW',
    payMethod: 'CARD',
    customer,
  });
}

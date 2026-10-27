import { axiosInstance } from '@/common/api/axiosInstance';
import { paymentApi, type PaymentConfirmResponse } from '@/common/api/clients';
import type { ApiResponse } from '@/common/api/types';
import { unwrap } from '@/common/api/unwrap';

// 백엔드 PaymentPrepareResponse. 생성 클라이언트에 아직 없어 직접 호출한다(oag 재생성 후 생성 코드로 바꾼다).
export interface PaymentPrepareResponse {
  orderId: number;
  portonePaymentId: string;
  orderName: string;
  amount: number;
  expiresAt: string;
}

// 결제하기를 누를 때마다 새 결제 시도를 만든다.
export async function preparePayment(orderId: number): Promise<PaymentPrepareResponse> {
  const { data } = await axiosInstance.post<ApiResponse<PaymentPrepareResponse>>(
    `/api/orders/${encodeURIComponent(orderId)}/payments`,
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

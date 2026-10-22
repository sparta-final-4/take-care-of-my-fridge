import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';

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

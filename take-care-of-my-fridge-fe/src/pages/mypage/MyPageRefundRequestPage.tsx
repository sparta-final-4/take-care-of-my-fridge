import { useParams } from 'react-router';

import { RefundRequestSection } from '@/domains/refund/components/RefundRequestSection';

export default function MyPageRefundRequestPage() {
  const { orderId } = useParams();

  return <RefundRequestSection orderId={Number(orderId)} />;
}

import { useParams } from 'react-router';

import { RefundDetailSection } from '@/domains/refund/components/RefundDetailSection';

export default function MyPageRefundDetailPage() {
  const { orderId } = useParams();

  return <RefundDetailSection orderId={Number(orderId)} />;
}

import { useParams } from 'react-router';

import { OrderDetailSection } from '@/domains/order/components/OrderDetailSection';

export default function MyPageOrderDetailPage() {
  const { orderId } = useParams();

  return <OrderDetailSection orderId={Number(orderId)} />;
}

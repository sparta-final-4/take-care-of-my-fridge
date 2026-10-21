import { useParams } from 'react-router';

import { OrderCheckoutSection } from '@/domains/order/components/OrderCheckoutSection';

export default function OrderCheckoutPage() {
  const { orderId } = useParams();

  return <OrderCheckoutSection orderId={Number(orderId)} />;
}

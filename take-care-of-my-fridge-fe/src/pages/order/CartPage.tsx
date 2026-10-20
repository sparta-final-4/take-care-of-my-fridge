import { Typography } from 'antd';

import { CartSection } from '@/domains/cart/components/CartSection';

import styles from './CartPage.module.scss';

export default function CartPage() {
  return (
    <div className={styles.page}>
      <Typography.Title level={2} className={styles.title}>
        장바구니
      </Typography.Title>
      <CartSection />
    </div>
  );
}

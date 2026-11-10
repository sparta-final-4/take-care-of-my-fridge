import { Flex, Typography } from 'antd';

import { SaleEventCreateSection } from '@/domains/sale/components/SaleEventCreateSection';

import styles from './AdminSaleEventCreatePage.module.scss';

export default function AdminSaleEventCreatePage() {
  return (
    <section className={styles.container}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.heading}>
        <Typography.Title level={2} className={styles.headingTitle}>
          할인 행사 관리
        </Typography.Title>
      </Flex>
      <SaleEventCreateSection />
    </section>
  );
}

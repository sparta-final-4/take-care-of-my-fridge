import { Typography } from 'antd';

import { SaleEventCreateSection } from '@/domains/sale/components/SaleEventCreateSection';

import styles from './AdminSaleEventCreatePage.module.scss';

export default function AdminSaleEventCreatePage() {
  return (
    <section className={styles.container}>
      <Typography.Title level={2}>할인 행사 관리</Typography.Title>
      <SaleEventCreateSection />
    </section>
  );
}

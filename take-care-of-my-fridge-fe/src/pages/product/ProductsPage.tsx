import { Typography } from 'antd';

import { ProductListSection } from '@/domains/product/components/ProductListSection';
import { ProductSearchSection } from '@/domains/product/components/ProductSearchSection';
import { TodayMealkitSection } from '@/domains/product/components/TodayMealkitSection';

import styles from './ProductsPage.module.scss';

export default function ProductsPage() {
  return (
    <div className={styles.page}>
      <Typography.Title level={2} className={styles.title}>
        재료/밀키트
      </Typography.Title>
      <ProductSearchSection />
      <TodayMealkitSection />
      <ProductListSection />
    </div>
  );
}

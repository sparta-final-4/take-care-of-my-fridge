import { ArrowRightOutlined } from '@ant-design/icons';
import { Button, Flex, Typography } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SaleEventCreateSection } from '@/domains/sale/components/SaleEventCreateSection';

import styles from './AdminSaleEventCreatePage.module.scss';

export default function AdminSaleEventCreatePage() {
  const navigate = useNavigate();
  return (
    <section className={styles.container}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.heading}>
        <Typography.Title level={2} className={styles.headingTitle}>
          할인 행사 관리
        </Typography.Title>
        <Button
          color="primary"
          variant="outlined"
          size="large"
          icon={<ArrowRightOutlined />}
          iconPlacement="end"
          className={styles.pageAction}
          onClick={() => navigate(ROUTES.adminDeals)}
        >
          할인 후보 보기
        </Button>
      </Flex>
      <SaleEventCreateSection />
    </section>
  );
}

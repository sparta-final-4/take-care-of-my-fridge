import { PlusOutlined } from '@ant-design/icons';
import { Button, Flex, Typography } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';

import { withoutBasicSeasoning } from '../filter';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems } from '../queries';
import styles from './FridgePageHeader.module.scss';

export function FridgePageHeader() {
  const navigate = useNavigate();
  const [sort] = useFridgeSort();
  const { data: allItems } = useFridgeItems(sort, true);
  // 요약 카드와 같은 기준으로 기본 양념은 빼고 센다.
  const data = allItems && withoutBasicSeasoning(allItems);
  const imminentCount = data?.filter((item) => item.status === 'IMMINENT').length ?? 0;
  const expiredCount = data?.filter((item) => item.status === 'EXPIRED').length ?? 0;

  return (
    <Flex align="center" justify="space-between" wrap gap="middle">
      <Flex align="baseline" wrap gap="small">
        <Typography.Title level={2} className={styles.title}>
          내 냉장고
        </Typography.Title>
        {data && (
          <Typography.Text type="secondary">
            재료 {data.length}개 · 임박 {imminentCount}개
            {expiredCount > 0 && ` · 기한 지남 ${expiredCount}개`}
          </Typography.Text>
        )}
      </Flex>
      {/* 재료 입력은 메인의 [냉장고에 뭐가 남았나요?]에서 한다. */}
      <Button
        type="primary"
        size="large"
        icon={<PlusOutlined />}
        onClick={() => navigate(ROUTES.home)}
      >
        재료 추가
      </Button>
    </Flex>
  );
}

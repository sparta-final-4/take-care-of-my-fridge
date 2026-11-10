import { PlusOutlined } from '@ant-design/icons';
import { Button, Flex, Typography } from 'antd';

import { withoutBasicSeasoning } from '../filter';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems } from '../queries';
import { FridgeIllustration } from './FridgeIllustration';
import styles from './FridgePageHeader.module.scss';

interface FridgePageHeaderProps {
  // [재료 추가]: 재료 추가 팝업을 연다.
  onAdd: () => void;
}

export function FridgePageHeader({ onAdd }: FridgePageHeaderProps) {
  const [sort] = useFridgeSort();
  const { data: allItems } = useFridgeItems(sort, true);
  // 요약 카드와 같은 기준으로 기본 양념은 빼고 센다.
  const data = allItems && withoutBasicSeasoning(allItems);
  const imminentCount = data?.filter((item) => item.status === 'IMMINENT').length ?? 0;
  const expiredCount = data?.filter((item) => item.status === 'EXPIRED').length ?? 0;

  return (
    <Flex align="center" justify="space-between" wrap gap="middle">
      {/* 제목 왼쪽에 웃는 냉장고 일러스트(장식) */}
      <Flex align="center" gap="middle" className={styles.titleGroup}>
        {/* 임박·기한 지난 재료가 있으면 땀 흘리는 표정 */}
        <FridgeIllustration
          mood={imminentCount + expiredCount > 0 ? 'worried' : 'happy'}
          className={styles.illustration}
        />
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
      </Flex>
      <Button type="primary" size="large" icon={<PlusOutlined />} onClick={onAdd}>
        재료 추가
      </Button>
    </Flex>
  );
}

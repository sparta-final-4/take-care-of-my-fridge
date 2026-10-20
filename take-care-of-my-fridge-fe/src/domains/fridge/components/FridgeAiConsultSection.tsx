import { Button, Flex, Tag, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';

import { formatDDay } from '../format';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems } from '../queries';
import styles from './FridgeAiConsultSection.module.scss';

export function FridgeAiConsultSection() {
  const [sort] = useFridgeSort();
  const { data } = useFridgeItems(sort, true);
  const imminentItems = data?.filter((item) => item.status === 'IMMINENT') ?? [];

  return (
    <SectionCard title="AI 냉장고 상담">
      <Flex vertical gap="middle">
        <Typography.Text>임박 재료로 만들 수 있는 메뉴를 추천해 드려요.</Typography.Text>
        {imminentItems.length > 0 ? (
          <Flex wrap gap="xs">
            {imminentItems.map((item) => (
              <Tag key={item.id} className={styles.chip}>
                {item.rawName} {formatDDay(item.dDay)}
              </Tag>
            ))}
          </Flex>
        ) : (
          <Typography.Text type="secondary">소비기한이 임박한 재료가 없어요.</Typography.Text>
        )}
        <Typography.Text type="secondary" className={styles.hint}>
          목록에서 재료를 골라 추천받을 수도 있어요.
        </Typography.Text>
        {/* TODO: AI 상담 기능이 생기면 상담 화면으로 연결한다. */}
        <Button type="primary" size="large" block>
          AI 상담 시작하기
        </Button>
      </Flex>
    </SectionCard>
  );
}

import { Button, Flex, Tag, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';

import { withoutBasicSeasoning } from '../filter';
import { formatDDayLabel } from '../format';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems } from '../queries';
import styles from './FridgeAiConsultSection.module.scss';

export function FridgeAiConsultSection() {
  const openChat = useChatModalStore((state) => state.openChat);
  const [sort] = useFridgeSort();
  const { data } = useFridgeItems(sort, true);
  // 밀키트·기본 양념은 메뉴 재료로 쓰지 않으므로 칩에서 뺀다.
  const imminentItems = withoutBasicSeasoning(data ?? []).filter(
    (item) => item.status === 'IMMINENT' && item.ingredientType !== 'MEALKIT',
  );

  return (
    <SectionCard title="AI 냉장고 상담">
      <Flex vertical gap="middle">
        <Typography.Text>임박 재료로 만들 수 있는 메뉴를 추천해 드려요.</Typography.Text>
        {imminentItems.length > 0 ? (
          <Flex wrap gap="xs">
            {imminentItems.map((item) => (
              <Tag key={item.id} className={styles.chip}>
                {item.rawName} {item.dDay !== undefined && formatDDayLabel(item.dDay)}
              </Tag>
            ))}
          </Flex>
        ) : (
          <Typography.Text type="secondary">소비기한이 임박한 재료가 없어요.</Typography.Text>
        )}
        <Typography.Text type="secondary" className={styles.hint}>
          목록에서 재료를 골라 추천받을 수도 있어요.
        </Typography.Text>
        <Button type="primary" size="large" onClick={openChat} block>
          AI 상담 시작하기
        </Button>
      </Flex>
    </SectionCard>
  );
}

import { Button, Flex, Tag, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';
import { toChatSelectedIngredients } from '@/domains/chat/selectedIngredients';

import { withoutBasicSeasoning } from '../filter';
import { formatDDayLabel } from '../format';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeSelectionStore } from '../model/useFridgeSelectionStore';
import { useFridgeItems } from '../queries';
import styles from './FridgeAiConsultSection.module.scss';

export function FridgeAiConsultSection() {
  const openChat = useChatModalStore((state) => state.openChat);
  const [sort] = useFridgeSort();
  const { data } = useFridgeItems(sort, true);
  const checkedIds = useFridgeSelectionStore((state) => state.checkedIds);
  const checkedItems = data?.filter((item) => checkedIds.includes(item.id ?? 0)) ?? [];
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
          {checkedItems.length > 0
            ? `목록에서 체크한 재료 ${checkedItems.length}개로 상담해요.`
            : '목록에서 재료를 체크하면 그 재료로 상담해요.'}
        </Typography.Text>
        {/* 체크한 재료가 있으면 그중 추천에 쓸 수 있는 것을 넘기고, 없으면 선택 없이 연다. */}
        <Button
          type="primary"
          size="large"
          onClick={() =>
            openChat(checkedItems.length > 0 ? toChatSelectedIngredients(checkedItems) : undefined)
          }
          block
        >
          AI 상담 시작하기
        </Button>
      </Flex>
    </SectionCard>
  );
}

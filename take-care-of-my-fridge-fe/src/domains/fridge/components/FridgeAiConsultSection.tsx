import { Button, Flex, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';
import { isChatSelectable, toChatSelectedIngredients } from '@/domains/chat/selectedIngredients';

import { imminentConsultText, selectedConsultText } from '../consultText';
import { withoutBasicSeasoning } from '../filter';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeSelectionStore } from '../model/useFridgeSelectionStore';
import { useFridgeItems } from '../queries';
import styles from './FridgeAiConsultSection.module.scss';

// 한 문장 + 버튼. 체크한 재료가 있으면 그 재료로, 없으면 임박 재료를 앞세워(없으면 냉장고 재료로) 상담을 권한다.
// 768px 이상에서는 체크한 재료를 이 카드에서 처리한다(휴대폰은 아래 선택 바).
export function FridgeAiConsultSection() {
  const openChat = useChatModalStore((state) => state.openChat);
  const [sort] = useFridgeSort();
  const { data } = useFridgeItems(sort, true);
  const checkedIds = useFridgeSelectionStore((state) => state.checkedIds);
  const clearChecked = useFridgeSelectionStore((state) => state.clear);
  const checkedItems = data?.filter((item) => checkedIds.includes(item.id ?? 0)) ?? [];

  if (checkedItems.length > 0) {
    const selected = toChatSelectedIngredients(checkedItems);
    // 직접 입력·밀키트·기본 양념은 레시피 재료로 찾을 수 없어 상담에 넘기지 않는다.
    const excludedCount = checkedItems.filter((item) => !isChatSelectable(item)).length;
    return (
      <SectionCard title="AI 냉장고 상담" bracket={false}>
        <Flex vertical gap="middle">
          <Typography.Text>
            {selectedConsultText(selected.map((ingredient) => ingredient.name))}
          </Typography.Text>
          {excludedCount > 0 && (
            <Typography.Text type="secondary" className={styles.hint}>
              체크한 재료 중 직접 입력·밀키트·기본 양념 {excludedCount}개는 빼고 상담해요.
            </Typography.Text>
          )}
          {/* 버튼은 사이트 기본 크기(44px), 카드 폭 그대로. 선택 해제는 오른쪽 작은 글자 링크 */}
          <Flex vertical gap="xs">
            <Button type="primary" block onClick={() => openChat(selected)}>
              선택한 재료로 상담하기
            </Button>
            <Button type="link" onClick={clearChecked} className={styles.clearLink}>
              선택 해제
            </Button>
          </Flex>
        </Flex>
      </SectionCard>
    );
  }

  // 밀키트·기본 양념은 메뉴 재료로 쓰지 않으므로 임박 재료에서 뺀다.
  const imminentItems = withoutBasicSeasoning(data ?? []).filter(
    (item) => item.status === 'IMMINENT' && item.ingredientType !== 'MEALKIT',
  );

  return (
    <SectionCard title="AI 냉장고 상담" bracket={false}>
      <Flex vertical gap="middle">
        <Typography.Text>{imminentConsultText(imminentItems)}</Typography.Text>
        <Typography.Text type="secondary" className={styles.hint}>
          목록에서 재료를 체크하면 그 재료로 상담해요.
        </Typography.Text>
        <Button type="primary" block onClick={() => openChat()}>
          AI 상담 시작하기
        </Button>
      </Flex>
    </SectionCard>
  );
}

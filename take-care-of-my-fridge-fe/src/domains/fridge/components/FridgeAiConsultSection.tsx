import { ArrowRightOutlined, RobotOutlined } from '@ant-design/icons';
import { Button, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';
import { isChatSelectable, toChatSelectedIngredients } from '@/domains/chat/selectedIngredients';

import { imminentConsultText, selectedConsultText } from '../consultText';
import { withoutBasicSeasoning } from '../filter';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeSelectionStore } from '../model/useFridgeSelectionStore';
import { useFridgeItems } from '../queries';
import styles from './FridgeAiConsultSection.module.scss';

// 옅은 주황 상자 하나: [상담 아이콘 + 한 문장] → [작은 상담 버튼 …… 선택 해제].
// 체크한 재료가 있으면 그 재료로, 없으면 임박 재료를 앞세워(없으면 냉장고 재료로) 상담을 권한다.
// 768px 이상에서는 체크한 재료를 이 카드에서 처리한다(휴대폰은 아래 선택 바).
export function FridgeAiConsultSection() {
  const openChat = useChatModalStore((state) => state.openChat);
  const [sort] = useFridgeSort();
  const { data } = useFridgeItems(sort, true);
  const checkedIds = useFridgeSelectionStore((state) => state.checkedIds);
  const clearChecked = useFridgeSelectionStore((state) => state.clear);
  const checkedItems = data?.filter((item) => checkedIds.includes(item.id ?? 0)) ?? [];
  const hasChecked = checkedItems.length > 0;

  // 체크한 재료 중 추천에 쓸 수 있는 것만 넘긴다. 직접 입력·밀키트·기본 양념은 빠진다.
  const selected = toChatSelectedIngredients(checkedItems);
  const excludedCount = checkedItems.filter((item) => !isChatSelectable(item)).length;
  // 밀키트·기본 양념은 메뉴 재료로 쓰지 않으므로 임박 재료에서 뺀다.
  const imminentItems = withoutBasicSeasoning(data ?? []).filter(
    (item) => item.status === 'IMMINENT' && item.ingredientType !== 'MEALKIT',
  );
  const message = hasChecked
    ? selectedConsultText(selected.map((ingredient) => ingredient.name))
    : imminentConsultText(imminentItems);

  return (
    <SectionCard title="AI 냉장고 상담" bracket={false}>
      <div className={styles.box}>
        <div className={styles.message}>
          <RobotOutlined aria-hidden className={styles.icon} />
          <Typography.Text className={styles.messageText}>{message}</Typography.Text>
        </div>
        {hasChecked && excludedCount > 0 && (
          <Typography.Text className={styles.hint}>
            체크한 재료 중 직접 입력·밀키트·기본 양념 {excludedCount}개는 빼고 상담해요.
          </Typography.Text>
        )}
        <div className={styles.actions}>
          <Button
            type="primary"
            icon={<ArrowRightOutlined />}
            iconPlacement="end"
            onClick={() => (hasChecked ? openChat(selected) : openChat())}
            className={styles.consultButton}
          >
            {hasChecked ? '상담하기' : 'AI 상담 시작하기'}
          </Button>
          {hasChecked && (
            <Button type="link" onClick={clearChecked} className={styles.clearLink}>
              선택 해제
            </Button>
          )}
        </div>
      </div>
      {!hasChecked && (
        <Typography.Text className={styles.footHint}>
          목록에서 재료를 체크하면 그 재료로 상담해요.
        </Typography.Text>
      )}
    </SectionCard>
  );
}

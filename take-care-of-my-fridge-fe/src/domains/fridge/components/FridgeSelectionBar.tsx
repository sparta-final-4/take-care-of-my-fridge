import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Button, Typography } from 'antd';

import { isChatSelectable } from '@/domains/chat/selectedIngredients';

import type { FridgeItemResponse } from '../api';
import styles from './FridgeSelectionBar.module.scss';

interface FridgeSelectionBarProps {
  // 체크한 재료(목록 순서)
  items: FridgeItemResponse[];
  // 여러 재료를 처리하는 중이면 버튼을 막는다.
  pending: boolean;
  onChat: () => void;
  onConsume: () => void;
  onDiscard: () => void;
  onClear: () => void;
}

// AI 상담에 넘기지 못하는 재료가 섞였을 때 한 줄 안내
function excludedChatNotice(items: FridgeItemResponse[]) {
  const excludedCount = items.filter((item) => !isChatSelectable(item)).length;
  if (excludedCount === 0) return undefined;
  if (excludedCount === items.length) {
    return '직접 입력·밀키트·기본 양념은 AI 상담에 넘길 수 없어 선택 없이 상담해요';
  }
  return `직접 입력·밀키트·기본 양념 ${excludedCount}개는 빼고 상담해요`;
}

// 재료를 체크하면 목록 아래에 붙는 막대: "N개 선택됨 · 이름" + [선택한 재료로 AI 상담] [먹었어요] [버리기]
export function FridgeSelectionBar({
  items,
  pending,
  onChat,
  onConsume,
  onDiscard,
  onClear,
}: FridgeSelectionBarProps) {
  const notice = excludedChatNotice(items);
  const names = items.map((item) => item.rawName).join(', ');

  return (
    <div className={styles.bar} role="region" aria-label="선택한 재료">
      <div className={styles.info}>
        <Typography.Text className={styles.summary} title={names}>
          <CheckOutlined /> {items.length}개 선택됨 · {names}
        </Typography.Text>
        {notice && <Typography.Text className={styles.notice}>{notice}</Typography.Text>}
      </div>
      <div className={styles.actions}>
        <Button type="primary" onClick={onChat} className={styles.chatButton}>
          선택한 재료로 AI 상담
        </Button>
        <Button ghost onClick={onConsume} disabled={pending}>
          먹었어요
        </Button>
        <Button ghost onClick={onDiscard} loading={pending} disabled={pending}>
          버리기
        </Button>
        <Button
          type="text"
          icon={<CloseOutlined />}
          aria-label="선택 해제"
          onClick={onClear}
          disabled={pending}
          className={styles.clearButton}
        />
      </div>
    </div>
  );
}

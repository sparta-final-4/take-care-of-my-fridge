import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Button, Typography } from 'antd';

import styles from './FridgeSelectionBar.module.scss';

interface FridgeSelectionBarProps {
  // 체크한 재료 수
  count: number;
  onChat: () => void;
  onClear: () => void;
}

// 휴대폰(768px 미만)에서 재료를 체크하면 목록 아래에 붙는 막대: "✓ N개 선택됨" + [AI 상담] [×]
// 768px 이상은 오른쪽 AI 상담 카드가 같은 일을 해서 숨긴다.
export function FridgeSelectionBar({ count, onChat, onClear }: FridgeSelectionBarProps) {
  return (
    <div className={styles.bar} role="region" aria-label="선택한 재료">
      <Typography.Text className={styles.summary}>
        <CheckOutlined /> {count}개 선택됨
      </Typography.Text>
      <Button type="primary" onClick={onChat}>
        AI 상담
      </Button>
      <Button
        type="text"
        icon={<CloseOutlined />}
        aria-label="선택 해제"
        onClick={onClear}
        className={styles.clearButton}
      />
    </div>
  );
}

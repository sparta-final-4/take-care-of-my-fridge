import { CheckCircleOutlined, DeleteOutlined, FireOutlined } from '@ant-design/icons';
import { Tag } from 'antd';

import type { FridgeItemAction } from '../api';
import styles from './FridgeLogActionTag.module.scss';

interface FridgeLogActionTagProps {
  action: FridgeItemAction;
  // 요리 완료로 빠진 기록. action은 CONSUMED로 오고 이 값으로만 구분된다.
  cookedByRecipe: boolean;
}

// 아이콘은 재료 목록의 먹었어요·버렸어요 버튼과 같은 것을 쓴다.
export function FridgeLogActionTag({ action, cookedByRecipe }: FridgeLogActionTagProps) {
  if (cookedByRecipe) {
    return (
      <Tag bordered={false} color="processing" icon={<FireOutlined />} className={styles.tag}>
        요리했어요
      </Tag>
    );
  }

  if (action === 'DISCARDED') {
    return (
      <Tag bordered={false} color="default" icon={<DeleteOutlined />} className={styles.tag}>
        버렸어요
      </Tag>
    );
  }

  return (
    <Tag bordered={false} color="success" icon={<CheckCircleOutlined />} className={styles.tag}>
      먹었어요
    </Tag>
  );
}

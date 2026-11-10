import { CheckCircleOutlined, DeleteOutlined, FireOutlined } from '@ant-design/icons';
import { Tag } from 'antd';

import type { FridgeItemAction } from '../api';
import styles from './FridgeLogActionTag.module.scss';

interface FridgeLogActionTagProps {
  action: FridgeItemAction;
  // 요리 완료로 빠진 기록. action은 CONSUMED로 오고 이 값으로만 구분된다.
  cookedByRecipe: boolean;
}

// 종류별 색: 요리했어요 초록, 먹었어요 주황, 버렸어요 빨강(옅은 바탕 + 진한 글자, 대비 4.5:1 이상).
export function FridgeLogActionTag({ action, cookedByRecipe }: FridgeLogActionTagProps) {
  if (cookedByRecipe) {
    return (
      <Tag icon={<FireOutlined />} className={`${styles.tag} ${styles.cooked}`}>
        요리했어요
      </Tag>
    );
  }

  if (action === 'DISCARDED') {
    return (
      <Tag icon={<DeleteOutlined />} className={`${styles.tag} ${styles.discarded}`}>
        버렸어요
      </Tag>
    );
  }

  return (
    <Tag icon={<CheckCircleOutlined />} className={`${styles.tag} ${styles.consumed}`}>
      먹었어요
    </Tag>
  );
}

import { Tag, Tooltip } from 'antd';

import type { IngredientType } from '../api';

import styles from './IngredientTypeTag.module.scss';

interface IngredientTypeTagProps {
  type?: IngredientType;
}

// 재료 이름 옆에는 밀키트만 표시한다(직접 입력 재료는 따로 표시하지 않는다).
export function IngredientTypeTag({ type }: IngredientTypeTagProps) {
  if (type !== 'MEALKIT') return null;

  return (
    <Tooltip title="밀키트는 요리 완료 때 차감되지 않아요">
      <Tag variant="filled" color="purple" className={styles.tag}>
        밀키트
      </Tag>
    </Tooltip>
  );
}

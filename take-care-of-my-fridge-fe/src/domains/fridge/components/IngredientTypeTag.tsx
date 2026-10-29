import { Tag, Tooltip } from 'antd';

import type { IngredientType } from '../api';

import styles from './IngredientTypeTag.module.scss';

interface IngredientTypeTagProps {
  type?: IngredientType;
}

export function IngredientTypeTag({ type }: IngredientTypeTagProps) {
  if (type === 'CUSTOM') {
    return (
      <Tooltip title="재료 사전에 없는 이름이라 레시피 추천과 요리 완료 차감에 쓰이지 않아요">
        <Tag bordered={false} color="default" className={styles.tag}>
          기타
        </Tag>
      </Tooltip>
    );
  }

  if (type === 'MEALKIT') {
    return (
      <Tooltip title="밀키트는 요리 완료 때 차감되지 않아요">
        <Tag bordered={false} color="purple" className={styles.tag}>
          밀키트
        </Tag>
      </Tooltip>
    );
  }

  return null;
}

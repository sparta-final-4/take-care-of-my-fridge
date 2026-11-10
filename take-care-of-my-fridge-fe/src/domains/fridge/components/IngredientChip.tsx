import { Tag, Tooltip } from 'antd';

import type { FridgeItemResponse } from '../api';
import { linkedIngredientName } from '../format';
import styles from './IngredientChip.module.scss';

interface IngredientChipProps {
  item: Pick<FridgeItemResponse, 'ingredientType' | 'ingredientName' | 'rawName'>;
  className?: string;
}

// 등록한 이름이 어떤 재료로 인식됐는지 보여 주는 칩.
//   일반 재료: 인식된 재료 이름(등록한 이름과 같아도 보여 준다), 연한 회색
//   CUSTOM: "기타"(재료 사전에 없는 이름), 연한 주황
//   MEALKIT: "밀키트", 보라
export function IngredientChip({ item, className }: IngredientChipProps) {
  const classes = (tone: string) => [styles.chip, tone, className].filter(Boolean).join(' ');

  if (item.ingredientType === 'CUSTOM') {
    return (
      <Tooltip title="재료 사전에 없는 이름이라 레시피 추천과 요리 완료 차감에 쓰이지 않아요">
        <Tag className={classes(styles.custom)}>기타</Tag>
      </Tooltip>
    );
  }

  if (item.ingredientType === 'MEALKIT') {
    return (
      <Tooltip title="밀키트는 요리 완료 때 차감되지 않아요">
        <Tag className={classes(styles.mealkit)}>밀키트</Tag>
      </Tooltip>
    );
  }

  const name = linkedIngredientName(item);
  return (
    <Tag title={name} className={classes(styles.normal)}>
      {name}
    </Tag>
  );
}

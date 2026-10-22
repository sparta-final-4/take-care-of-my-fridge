import { CheckOutlined } from '@ant-design/icons';
import { Tag, Typography } from 'antd';

import type { IngredientItem } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { formatDDay } from '@/domains/fridge/format';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeIngredientSection.module.scss';

// 내 냉장고와 같은 기준(3일 이내)으로 임박 재료를 강조한다.
const IMMINENT_DAYS = 3;

interface RecipeIngredientSectionProps {
  ingredients: IngredientItem[];
  fridgeMatch: RecipeFridgeMatch;
}

export function RecipeIngredientSection({
  ingredients,
  fridgeMatch,
}: RecipeIngredientSectionProps) {
  const { checked, dDayOf, heldIds, totalCount } = fridgeMatch;

  return (
    <SectionCard
      title="재료"
      extra={
        <Typography.Text type="secondary">
          {checked
            ? `내 냉장고 ${heldIds.length}/${totalCount} 보유`
            : '로그인하면 보유 여부를 함께 보여 줘요'}
        </Typography.Text>
      }
    >
      <ul className={styles.list}>
        {ingredients.map((item) => {
          const dDay = dDayOf(item.ingredientId);
          const held = dDay !== undefined;
          return (
            <li key={item.seq} className={styles.row}>
              <Typography.Text strong className={styles.name}>
                {item.displayName}
              </Typography.Text>
              <Typography.Text type="secondary" className={styles.amount}>
                {item.amount}
              </Typography.Text>
              {checked && (
                <span className={styles.status}>
                  {held && dDay <= IMMINENT_DAYS && (
                    <Tag color={dDay <= 1 ? 'red' : 'orange'} className={styles.dDay}>
                      {formatDDay(dDay)}
                    </Tag>
                  )}
                  {held ? (
                    <Typography.Text type="success">
                      <CheckOutlined /> 보유
                    </Typography.Text>
                  ) : (
                    <Typography.Text type="secondary">없음</Typography.Text>
                  )}
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </SectionCard>
  );
}

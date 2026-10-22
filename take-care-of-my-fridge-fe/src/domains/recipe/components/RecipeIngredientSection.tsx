import { CheckOutlined } from '@ant-design/icons';
import { Tag, Typography } from 'antd';

import type { IngredientItem } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { formatDDay } from '@/domains/fridge/format';
import { imminentTagColor } from '@/domains/recipe/format';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeIngredientSection.module.scss';

interface RecipeIngredientSectionProps {
  ingredients: IngredientItem[];
  fridgeMatch: RecipeFridgeMatch;
}

export function RecipeIngredientSection({
  ingredients,
  fridgeMatch,
}: RecipeIngredientSectionProps) {
  const { checked, fridgeItemOf, heldIds, totalCount } = fridgeMatch;

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
          const fridgeItem = fridgeItemOf(item.ingredientId);
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
                  {fridgeItem?.status === 'IMMINENT' && (
                    <Tag color={imminentTagColor(fridgeItem.dDay)} className={styles.dDay}>
                      {formatDDay(fridgeItem.dDay)}
                    </Tag>
                  )}
                  {fridgeItem ? (
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

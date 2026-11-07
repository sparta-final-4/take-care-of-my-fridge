import { CheckOutlined } from '@ant-design/icons';
import { Tag, Typography } from 'antd';

import type { IngredientItem } from '@/common/api/clients';
import { formatDDay } from '@/domains/fridge/format';
import { imminentTagColor } from '@/domains/recipe/format';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeIngredientSection.module.scss';

interface RecipeIngredientSectionProps {
  ingredients: IngredientItem[];
  fridgeMatch: RecipeFridgeMatch;
}

// 레퍼런스 레시피 글의 재료 칸: 상자 없이 한 줄에 재료 하나, 이름 | 양 | 내 냉장고 보유 여부.
// 서버가 재료 묶음 이름(주재료·양념 등)을 보내면 묶음마다 작은 제목을 단다.
export function RecipeIngredientSection({
  ingredients,
  fridgeMatch,
}: RecipeIngredientSectionProps) {
  const { checked, fridgeItemOf, heldIds, totalCount } = fridgeMatch;

  // 받은 순서를 지키면서 묶음 이름별로 모은다.
  const groups: Array<{ name: string; items: IngredientItem[] }> = [];
  ingredients.forEach((item) => {
    const name = item.groupName ?? '';
    const group = groups.find((each) => each.name === name);
    if (group) group.items.push(item);
    else groups.push({ name, items: [item] });
  });

  return (
    <section className={styles.section} aria-label="재료">
      <div className={styles.head}>
        <Typography.Title level={2} className={styles.title}>
          재료
        </Typography.Title>
        <Typography.Text type="secondary" className={styles.note}>
          {checked
            ? `내 냉장고 ${heldIds.length}/${totalCount} 보유`
            : '로그인하면 보유 여부를 함께 보여 줘요'}
        </Typography.Text>
      </div>
      {groups.map((group) => (
        <div key={group.name || 'default'} className={styles.group}>
          {group.name && (
            <Typography.Text className={styles.groupName}>{group.name}</Typography.Text>
          )}
          <ul className={styles.list}>
            {group.items.map((item) => {
              const fridgeItem = fridgeItemOf(item.ingredientId);
              return (
                <li key={item.seq} className={styles.row}>
                  <Typography.Text className={styles.name}>{item.displayName}</Typography.Text>
                  <Typography.Text className={styles.amount}>{item.amount}</Typography.Text>
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
        </div>
      ))}
    </section>
  );
}

import { Typography } from 'antd';

import type { Nutrition } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';

import styles from './RecipeNutritionSection.module.scss';

const NUTRITION_ITEMS: Array<{ key: keyof Nutrition; label: string; unit: string }> = [
  { key: 'calories', label: '열량', unit: 'kcal' },
  { key: 'carbsG', label: '탄수화물', unit: 'g' },
  { key: 'proteinG', label: '단백질', unit: 'g' },
  { key: 'fatG', label: '지방', unit: 'g' },
  { key: 'sodiumMg', label: '나트륨', unit: 'mg' },
  { key: 'weightG', label: '중량', unit: 'g' },
];

interface RecipeNutritionSectionProps {
  nutrition?: Nutrition;
}

// 백엔드 값은 1인분 기준이다. 값이 있는 항목만 보여 주고, 하나도 없으면 섹션을 숨긴다.
export function RecipeNutritionSection({ nutrition }: RecipeNutritionSectionProps) {
  const items = NUTRITION_ITEMS.flatMap((item) => {
    const value = nutrition?.[item.key];
    return value === undefined || value === null ? [] : [{ ...item, value }];
  });
  if (items.length === 0) return null;

  return (
    <SectionCard
      title="영양 정보"
      extra={<Typography.Text type="secondary">1인분 기준</Typography.Text>}
    >
      <dl className={styles.list}>
        {items.map((item) => (
          <div key={item.key} className={styles.item}>
            <dt>
              <Typography.Text type="secondary">{item.label}</Typography.Text>
            </dt>
            <dd className={styles.value}>
              <Typography.Text strong>
                {item.value.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}
              </Typography.Text>{' '}
              <Typography.Text type="secondary">{item.unit}</Typography.Text>
            </dd>
          </div>
        ))}
      </dl>
    </SectionCard>
  );
}

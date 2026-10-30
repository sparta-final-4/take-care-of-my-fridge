import { Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { PopularRecipeSection } from '@/domains/cooking/components/PopularRecipeSection';
import { IngredientInputSection } from '@/domains/fridge/components/IngredientInputSection';
import { MyFridgeSection } from '@/domains/fridge/components/MyFridgeSection';
import { PopularSaleSection } from '@/domains/sale/components/PopularSaleSection';

import styles from './MainPage.module.scss';

export default function MainPage() {
  return (
    <div className={styles.page}>
      <div className={styles.row}>
        <div className={styles.leftCol}>
          <IngredientInputSection />
        </div>
        <div className={styles.rightCol}>
          <MyFridgeSection />
        </div>
      </div>

      <div className={styles.row}>
        <div className={styles.leftCol}>
          <SectionCard title="지금 냉장고로 만들 수 있는 메뉴">
            <Typography.Text type="secondary">준비 중</Typography.Text>
          </SectionCard>
        </div>
        <div className={styles.rightStack}>
          <div className={styles.rightStackItem}>
            <PopularSaleSection />
          </div>
          <div className={styles.rightStackItem}>
            <PopularRecipeSection />
          </div>
        </div>
      </div>
    </div>
  );
}

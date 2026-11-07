import { FridgeAiConsultSection } from '@/domains/fridge/components/FridgeAiConsultSection';
import { FridgeItemListSection } from '@/domains/fridge/components/FridgeItemListSection';
import { FridgePageHeader } from '@/domains/fridge/components/FridgePageHeader';
import { FridgeSummarySection } from '@/domains/fridge/components/FridgeSummarySection';
import { IngredientInputSection } from '@/domains/fridge/components/IngredientInputSection';
import { RecentFridgeLogSection } from '@/domains/fridge/components/RecentFridgeLogSection';

import styles from './FridgePage.module.scss';

// 재료가 많아도 읽기 쉽게: 제목 → 요약 숫자 띠 → 재료 입력 → [재료 목록(넓게) | AI 상담·최근 기록(스크롤해도 옆에 붙음)].
export default function FridgePage() {
  return (
    <div className={styles.page}>
      <FridgePageHeader />
      <FridgeSummarySection />
      <IngredientInputSection />
      <div className={styles.row}>
        <div className={styles.leftCol}>
          <FridgeItemListSection />
        </div>
        <aside className={styles.aside} aria-label="냉장고 도움">
          <FridgeAiConsultSection />
          <RecentFridgeLogSection />
        </aside>
      </div>
    </div>
  );
}

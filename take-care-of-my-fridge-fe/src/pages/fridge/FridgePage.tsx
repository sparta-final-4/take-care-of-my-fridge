import { useState } from 'react';

import { FridgeAddModal } from '@/domains/fridge/components/FridgeAddModal';
import { FridgeAiConsultSection } from '@/domains/fridge/components/FridgeAiConsultSection';
import { FridgeItemListSection } from '@/domains/fridge/components/FridgeItemListSection';
import { FridgePageHeader } from '@/domains/fridge/components/FridgePageHeader';
import { FridgeSummarySection } from '@/domains/fridge/components/FridgeSummarySection';
import { RecentFridgeLogSection } from '@/domains/fridge/components/RecentFridgeLogSection';

import styles from './FridgePage.module.scss';

// 재료가 많아도 읽기 쉽게: 제목 → 요약 숫자 띠 → [재료 목록(넓게) | AI 상담·최근 기록(스크롤해도 옆에 붙음)].
// 재료 추가는 제목 옆 [재료 추가]·빈 냉장고 안내에서 여는 팝업에서 한다.
export default function FridgePage() {
  const [addOpen, setAddOpen] = useState(false);
  const openAdd = () => setAddOpen(true);

  return (
    <div className={styles.page}>
      <FridgePageHeader onAdd={openAdd} />
      <FridgeSummarySection />
      <div className={styles.row}>
        <div className={styles.leftCol}>
          <FridgeItemListSection onAdd={openAdd} />
        </div>
        <aside className={styles.aside} aria-label="냉장고 도움">
          <FridgeAiConsultSection />
          <RecentFridgeLogSection />
        </aside>
      </div>
      <FridgeAddModal open={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  );
}

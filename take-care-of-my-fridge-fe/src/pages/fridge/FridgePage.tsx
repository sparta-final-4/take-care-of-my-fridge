import { FridgeAiConsultSection } from '@/domains/fridge/components/FridgeAiConsultSection';
import { FridgeItemListSection } from '@/domains/fridge/components/FridgeItemListSection';
import { FridgePageHeader } from '@/domains/fridge/components/FridgePageHeader';
import { FridgeSummarySection } from '@/domains/fridge/components/FridgeSummarySection';
import { RecentFridgeLogSection } from '@/domains/fridge/components/RecentFridgeLogSection';

import styles from './FridgePage.module.scss';

export default function FridgePage() {
  return (
    <div className={styles.page}>
      <FridgePageHeader />
      <div className={styles.row}>
        <div className={styles.leftCol}>
          <FridgeItemListSection />
        </div>
        <div className={styles.rightStack}>
          <FridgeSummarySection />
          <FridgeAiConsultSection />
          <RecentFridgeLogSection />
        </div>
      </div>
    </div>
  );
}

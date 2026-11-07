import { useQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/common/auth/authStore';
import { MyFridgeSection } from '@/domains/fridge/components/MyFridgeSection';
import { PopularSaleSection } from '@/domains/sale/components/PopularSaleSection';
import { saleQueries } from '@/domains/sale/queries';

import { MainConsultBand } from './main/MainConsultBand';
import { MainHero } from './main/MainHero';
import { MainRecipeBand } from './main/MainRecipeBand';
import { MainShortcuts } from './main/MainShortcuts';
import styles from './MainPage.module.scss';

// 메인은 기능을 늘어놓지 않고 핵심 세 가지로 보낸다(레퍼런스 row7seeds.com의 구성).
// 사진 첫 화면 → (로그인하면) 내 냉장고 임박 재료 → 핵심 기능 바로가기 3칸(종이색) → AI 상담(잉크색)
// → 지금 뜨는 할인(종이색) → 많이 만든 레시피 사진 카드(종이색). 맨 아래 서비스 소개(흰색)는 모든 화면 공통(SiteFooter).
export default function MainPage() {
  const isAuthenticated = useAuthStore((state) => state.status === 'authenticated');
  // PopularSaleSection과 같은 조회라 요청은 한 번만 간다. 진행 중인 할인이 없으면 띠를 통째로 숨긴다.
  const { data: sales, isSuccess: salesLoaded } = useQuery(saleQueries.popular());
  const hasNoSales = salesLoaded && (sales?.length ?? 0) === 0;

  return (
    <div className={styles.page}>
      <MainHero />

      {/* 로그인한 회원에게만: 첫 화면 바로 아래에 소비기한 임박 재료를 보여 준다. */}
      {isAuthenticated && (
        <div className={styles.fridgeBand}>
          <div className={styles.fridgeInner}>
            <MyFridgeSection />
          </div>
        </div>
      )}

      <MainShortcuts />
      <MainConsultBand />

      {!hasNoSales && (
        <div className={styles.saleBand}>
          <div className={styles.saleInner}>
            <PopularSaleSection />
          </div>
        </div>
      )}

      <div className={styles.recipeSlot}>
        <MainRecipeBand />
      </div>
    </div>
  );
}

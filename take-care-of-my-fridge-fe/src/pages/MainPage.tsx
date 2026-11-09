import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';

import { addSectionSnap } from '@/common/scroll/smoothScroll';
import { TodayMealkitSection } from '@/domains/product/components/TodayMealkitSection';
import { PopularSaleSection } from '@/domains/sale/components/PopularSaleSection';
import { saleQueries } from '@/domains/sale/queries';

import { MainConsultBand } from './main/MainConsultBand';
import { MainHero } from './main/MainHero';
import { MainRecipeBand } from './main/MainRecipeBand';
import { MainSectionDots } from './main/MainSectionDots';
import { MainShortcuts } from './main/MainShortcuts';
import styles from './MainPage.module.scss';

// 메인은 기능을 늘어놓지 않고 핵심 세 가지로 보낸다(레퍼런스 row7seeds.com의 구성).
// 한 화면을 채우는 사진 첫 화면 → 핵심 기능 바로가기 3칸(종이색) → 한 화면을 채우는 AI 상담(잉크색, 사진 절반 · 글 절반)
// → 지금 뜨는 할인(종이색) → 오늘의 밀키트(종이색) → 많이 만든 레시피 사진 카드(종이색). 맨 아래 서비스 소개(흰색)는 모든 화면 공통(SiteFooter).
export default function MainPage() {
  // PopularSaleSection과 같은 조회라 요청은 한 번만 간다. 진행 중인 할인이 없으면 띠를 통째로 숨긴다.
  const { data: sales, isSuccess: salesLoaded } = useQuery(saleQueries.popular());
  const hasNoSales = salesLoaded && (sales?.length ?? 0) === 0;
  // 휠을 멈춘 곳이 띠 시작 근처면 그 띠에 살짝 붙인다. 할인 여부로 띠가 바뀌면 다시 붙인다.
  const pageRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const bands = Array.from(pageRef.current?.children ?? []).filter(
      (child): child is HTMLElement => child instanceof HTMLElement,
    );
    return addSectionSnap(bands);
  }, [hasNoSales]);

  return (
    <>
      <div ref={pageRef} className={styles.page}>
        <MainHero />

        <MainShortcuts />
        <MainConsultBand />

        {!hasNoSales && (
          <div className={styles.saleBand}>
            <div className={styles.saleInner}>
              <PopularSaleSection />
            </div>
          </div>
        )}

        <div className={styles.mealkitBand}>
          <div className={styles.mealkitInner}>
            <TodayMealkitSection />
          </div>
        </div>

        <div className={styles.recipeSlot}>
          <MainRecipeBand />
        </div>
      </div>

      {/* 띠 목록(page의 자식)에 섞이지 않게 바깥에 둔다. */}
      <MainSectionDots pageRef={pageRef} />
    </>
  );
}

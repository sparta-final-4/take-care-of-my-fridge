import { Layout } from 'antd';
import { useEffect, useRef } from 'react';
import { Outlet, useLocation, useMatches, useNavigation, useNavigationType } from 'react-router';

import { ROUTES } from '@/app/paths';
import { NavigationProgress } from '@/common/components/NavigationProgress';
import { RouteFallback } from '@/common/components/RouteFallback';
import { scrollToTopInstantly } from '@/common/scroll/smoothScroll';

import { ChatModal } from '@/domains/chat/components/ChatModal';

import { Header } from './Header';
import styles from './MainLayout.module.scss';
import { SiteFooter } from './SiteFooter';

// router.tsx의 route id. 이 안의 이동은 본문을 다시 그리지 않는다.
// 마이페이지: MyPageLayout이 전환 효과를 맡는다. 재료/밀키트: 상품 모달 주소로 바뀌어도 목록을 그대로 둔다.
const STABLE_ROUTE_IDS = ['mypage-layout', 'products'];

export function MainLayout() {
  const navigation = useNavigation();
  const matches = useMatches();
  const navigationType = useNavigationType();
  const { pathname } = useLocation();
  // 다른 화면으로 바뀔 때만 본문을 다시 그려 진입 효과를 준다. 같은 화면의 파라미터·쿼리 변경은 유지한다.
  const pageKey =
    matches.find((match) => STABLE_ROUTE_IDS.includes(match.id))?.id ?? matches.at(-1)?.id;
  // 주소가 바뀌면 맨 위에서 시작한다. 쿼리(필터·페이지)만 바뀔 때와 상품 목록 ↔ 상품 모달은 그대로 두고,
  // 뒤로·앞으로 가기(POP)는 브라우저가 이전 위치로 돌려놓게 둔다.
  const isProducts = matches.some((match) => match.id === 'products');
  const scrollKey = isProducts ? 'products' : pathname;
  const lastScrollKey = useRef(scrollKey);
  useEffect(() => {
    if (lastScrollKey.current === scrollKey) return;
    lastScrollKey.current = scrollKey;
    if (navigationType !== 'POP') scrollToTopInstantly();
  }, [scrollKey, navigationType]);
  // 메인은 사진·색 띠가 화면 폭 끝까지 가도록 본문 폭 제한과 여백을 풀고, 띠마다 안쪽에서 폭을 맞춘다.
  const isHome = pathname === ROUTES.home;
  const contentClassName = [
    styles.content,
    isHome && styles.fullBleed,
    navigation.state === 'loading' && styles.pending,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <Layout className={styles.layout}>
      <NavigationProgress />
      <Header />
      <Layout.Content key={pageKey} className={contentClassName}>
        <Outlet />
      </Layout.Content>
      {/* 모든 화면 맨 아래 흰 띠: 서비스 소개 · 바로가기 · 사진 출처 */}
      <SiteFooter />
      <ChatModal />
    </Layout>
  );
}

// 새로고침 등 첫 진입에서 화면 코드를 받는 동안 헤더와 함께 보여 준다.
export function MainLayoutFallback() {
  return (
    <Layout className={styles.layout}>
      <Header />
      <Layout.Content className={styles.content}>
        <RouteFallback />
      </Layout.Content>
    </Layout>
  );
}

import { Layout } from 'antd';
import { Outlet, useMatches, useNavigation } from 'react-router';

import { NavigationProgress } from '@/common/components/NavigationProgress';
import { RouteFallback } from '@/common/components/RouteFallback';

import { Header } from './Header';
import styles from './MainLayout.module.scss';

// router.tsx의 마이페이지 레이아웃 route id. 마이페이지 안의 이동은 MyPageLayout이 전환 효과를 맡는다.
const MYPAGE_LAYOUT_ROUTE_ID = 'mypage-layout';

export function MainLayout() {
  const navigation = useNavigation();
  const matches = useMatches();
  // 다른 화면으로 바뀔 때만 본문을 다시 그려 진입 효과를 준다. 같은 화면의 파라미터·쿼리 변경은 유지한다.
  const pageKey = matches.some((match) => match.id === MYPAGE_LAYOUT_ROUTE_ID)
    ? MYPAGE_LAYOUT_ROUTE_ID
    : matches.at(-1)?.id;
  const contentClassName =
    navigation.state === 'loading' ? `${styles.content} ${styles.pending}` : styles.content;

  return (
    <Layout className={styles.layout}>
      <NavigationProgress />
      <Header />
      <Layout.Content key={pageKey} className={contentClassName}>
        <Outlet />
      </Layout.Content>
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

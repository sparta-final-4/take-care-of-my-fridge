import { Typography } from 'antd';
import { NavLink, Outlet, useMatches } from 'react-router';

import { ROUTES } from '@/app/paths';

import styles from './MyPageLayout.module.scss';

interface MyPageNavItem {
  key: string;
  label: string;
  to: string;
}

const MYPAGE_NAV_ITEMS: readonly MyPageNavItem[] = [
  { key: 'recipes', label: '내 레시피 관리', to: ROUTES.mypageRecipes },
  { key: 'chats', label: 'AI 상담 내역', to: ROUTES.mypageChats },
  { key: 'profile', label: '내 정보 수정', to: ROUTES.mypageProfile },
  { key: 'orders', label: '주문 내역', to: ROUTES.mypageOrders },
] as const;

export function MyPageLayout() {
  // 메뉴는 그대로 두고 본문만 다시 그려 진입 효과를 준다.
  const pageKey = useMatches().at(-1)?.id;

  return (
    <div className={styles.container}>
      <aside className={styles.sidebar} aria-label="마이페이지 메뉴">
        <Typography.Title level={4} className={styles.title}>
          마이페이지
        </Typography.Title>
        <nav className={styles.nav}>
          {MYPAGE_NAV_ITEMS.map((item) => (
            <NavLink
              key={item.key}
              to={item.to}
              className={({ isActive }) =>
                isActive ? `${styles.navItem} ${styles.navItemActive}` : styles.navItem
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <main key={pageKey} className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}

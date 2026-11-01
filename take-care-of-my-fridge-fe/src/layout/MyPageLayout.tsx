import type { ReactNode } from 'react';
import { BookOutlined, MessageOutlined, ShoppingOutlined, UserOutlined } from '@ant-design/icons';
import { NavLink, Outlet, useMatches } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';

import styles from './MyPageLayout.module.scss';

interface MyPageNavItem {
  key: string;
  label: string;
  to: string;
  icon: ReactNode;
}

const MYPAGE_NAV_ITEMS: readonly MyPageNavItem[] = [
  { key: 'recipes', label: '내 레시피 관리', to: ROUTES.mypageRecipes, icon: <BookOutlined /> },
  { key: 'chats', label: 'AI 상담 내역', to: ROUTES.mypageChats, icon: <MessageOutlined /> },
  { key: 'profile', label: '내 정보 수정', to: ROUTES.mypageProfile, icon: <UserOutlined /> },
  { key: 'orders', label: '주문 내역', to: ROUTES.mypageOrders, icon: <ShoppingOutlined /> },
] as const;

export function MyPageLayout() {
  // 메뉴는 그대로 두고 본문만 다시 그려 진입 효과를 준다.
  const pageKey = useMatches().at(-1)?.id;

  return (
    <div className={styles.container}>
      {/* 오른쪽 내용과 같은 카드(SectionCard). md부터 스크롤해도 헤더 아래에 붙어 있다. */}
      <aside className={styles.sidebar} aria-label="마이페이지 메뉴">
        <SectionCard title="마이페이지" bracket={false}>
          <nav className={styles.nav}>
            {MYPAGE_NAV_ITEMS.map((item) => (
              <NavLink
                key={item.key}
                to={item.to}
                className={({ isActive }) =>
                  isActive ? `${styles.navItem} ${styles.navItemActive}` : styles.navItem
                }
              >
                <span className={styles.navIcon} aria-hidden="true">
                  {item.icon}
                </span>
                {item.label}
              </NavLink>
            ))}
          </nav>
        </SectionCard>
      </aside>

      <main key={pageKey} className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}

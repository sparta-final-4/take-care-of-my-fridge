import { MenuOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Badge, Button, Drawer, Flex, Grid, Input, Layout, Typography } from 'antd';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { setSession } from '@/common/auth/session';
import { sessionId } from '@/common/auth/sessionId';
import { logout } from '@/domains/auth/api';
import { NotificationBell } from '@/domains/notification/components/NotificationBell';

import styles from './Header.module.scss';

const CART_COUNT = 2;

const NAV_ITEMS = [
  { key: 'fridge', label: '내 냉장고' },
  { key: 'recipes', label: '레시피' },
  { key: 'products', label: '재료/밀키트' },
  { key: 'cart', label: '장바구니', badgeCount: CART_COUNT },
  { key: 'mypage', label: '마이페이지' },
] as const;

export function Header() {
  const navigate = useNavigate();
  const { status, member } = useAuthStore();
  const logoutMutation = useMutation({ mutationFn: logout });
  const screens = Grid.useBreakpoint();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleOpenDrawer = () => setDrawerOpen(true);
  const handleCloseDrawer = () => setDrawerOpen(false);
  const handleLogout = () => {
    const id = sessionId();
    logoutMutation.mutate(undefined, {
      onSuccess: async () => {
        if (id !== sessionId()) return;
        await setSession(null);
        navigate(ROUTES.login);
      },
    });
  };

  return (
    <Layout.Header className={styles.header}>
      <div className={styles.inner}>
        <Link to={ROUTES.home} className={styles.brand}>
          <span className={styles.logoMark} aria-hidden="true">
            <span className={styles.logoDoor} />
          </span>
          <Typography.Text strong className={styles.brandText}>
            냉장고를 부탁해
          </Typography.Text>
        </Link>

        <div className={styles.searchWrapper}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="재료나 요리를 검색해 보세요"
            aria-label="재료나 요리 검색"
            className={styles.searchInput}
          />
        </div>

        <nav className={styles.desktopNav} aria-label="주요 메뉴">
          {NAV_ITEMS.map((item) => (
            <Button
              key={item.key}
              type="text"
              className={styles.navButton}
              onClick={
                item.key === 'mypage'
                  ? () => navigate(status === 'authenticated' ? ROUTES.account : ROUTES.login)
                  : undefined
              }
            >
              <Flex align="center" gap="small">
                <span>{item.label}</span>
                {'badgeCount' in item && <Badge count={item.badgeCount} size="small" />}
              </Flex>
            </Button>
          ))}
        </nav>

        <Flex align="center" gap="small" className={styles.actions}>
          {screens.lg && member?.role === 'ADMIN' && (
            <>
              <Button type="text" onClick={() => navigate(ROUTES.adminMembers)}>
                회원 관리
              </Button>
              <Button type="text" onClick={() => navigate(ROUTES.adminAudit)}>
                작업 이력
              </Button>
            </>
          )}
          {screens.lg && status === 'authenticated' && (
            <Button type="text" onClick={handleLogout} loading={logoutMutation.isPending}>
              {member?.nickname} 로그아웃
            </Button>
          )}
          <NotificationBell />
          {!screens.lg && (
            <Button
              type="text"
              icon={<MenuOutlined />}
              aria-label="메뉴 열기"
              onClick={handleOpenDrawer}
              className={styles.mobileMenuTrigger}
            />
          )}
        </Flex>
      </div>

      <Drawer
        title="냉장고를 부탁해"
        placement="right"
        open={!screens.lg && drawerOpen}
        onClose={handleCloseDrawer}
      >
        <Flex vertical gap="middle">
          <Input
            prefix={<SearchOutlined />}
            placeholder="재료나 요리를 검색해 보세요"
            aria-label="모바일 재료나 요리 검색"
          />
          <Flex vertical gap="xs" role="navigation" aria-label="모바일 메뉴">
            {NAV_ITEMS.map((item) => (
              <Button
                key={item.key}
                type="text"
                block
                onClick={() => {
                  handleCloseDrawer();
                  if (item.key === 'mypage')
                    navigate(status === 'authenticated' ? ROUTES.account : ROUTES.login);
                }}
                className={styles.drawerNavButton}
              >
                <Flex align="center" justify="space-between" className={styles.drawerNavInner}>
                  <span>{item.label}</span>
                  {'badgeCount' in item && <Badge count={item.badgeCount} size="small" />}
                </Flex>
              </Button>
            ))}
            {member?.role === 'ADMIN' && (
              <Button
                type="text"
                block
                onClick={() => {
                  handleCloseDrawer();
                  navigate(ROUTES.adminMembers);
                }}
              >
                회원 관리
              </Button>
            )}
            {member?.role === 'ADMIN' && (
              <Button
                type="text"
                block
                onClick={() => {
                  handleCloseDrawer();
                  navigate(ROUTES.adminAudit);
                }}
              >
                작업 이력
              </Button>
            )}
            {status === 'authenticated' && (
              <Button
                type="text"
                block
                onClick={() => {
                  handleCloseDrawer();
                  handleLogout();
                }}
                loading={logoutMutation.isPending}
              >
                로그아웃
              </Button>
            )}
          </Flex>
        </Flex>
      </Drawer>
    </Layout.Header>
  );
}

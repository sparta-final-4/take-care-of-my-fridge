import { MenuOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Badge, Button, Divider, Drawer, Flex, Grid, Input, Layout, Typography } from 'antd';
import { useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { setSession } from '@/common/auth/session';
import { sessionId } from '@/common/auth/sessionId';
import { logout } from '@/domains/auth/api';
import { cartQueries } from '@/domains/cart/queries';
import { NotificationBell } from '@/domains/notification/components/NotificationBell';

import styles from './Header.module.scss';

const NAV_ITEMS = [
  { key: 'fridge', label: '내 냉장고' },
  { key: 'recipes', label: '레시피' },
  { key: 'products', label: '재료/밀키트' },
  { key: 'cart', label: '장바구니' },
  { key: 'mypage', label: '마이페이지' },
] as const;

// 관리자에게는 이 메뉴만 보인다. 경로가 activePaths 중 하나로 시작하면 현재 위치로 표시한다(하위 화면 포함).
const ADMIN_NAV_ITEMS = [
  { label: '회원 관리', path: ROUTES.adminMembers, activePaths: [ROUTES.adminMembers] },
  { label: '작업 이력', path: ROUTES.adminAudit, activePaths: [ROUTES.adminAudit] },
  { label: '판매 예측', path: ROUTES.adminForecast, activePaths: [ROUTES.adminForecast] },
  { label: '상품 관리', path: ROUTES.adminProducts, activePaths: [ROUTES.adminProducts] },
  {
    label: '할인 행사',
    path: ROUTES.adminSaleEventNew,
    activePaths: [ROUTES.adminSaleEventNew, ROUTES.adminDeals],
  },
  {
    label: '후기 요약',
    path: ROUTES.adminReviewSummary,
    activePaths: [ROUTES.adminReviewSummary],
  },
  { label: '정산', path: ROUTES.adminSettlements, activePaths: [ROUTES.adminSettlements] },
] as const;

// 백엔드 검색어 길이 제한(RecipeListRequest.keyword)
const SEARCH_KEYWORD_MAX_LENGTH = 50;

export function Header() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  // 검색 화면에서는 현재 검색어를 입력칸에 보여 주고, 다른 화면으로 가면 비운다.
  const currentKeyword = pathname === ROUTES.search ? (searchParams.get('keyword') ?? '') : '';
  const status = useAuthStore((state) => state.status);
  const role = useAuthStore((state) => state.member?.role);
  const nickname = useAuthStore((state) => state.member?.nickname);
  const logoutMutation = useMutation({ mutationFn: logout });
  const screens = Grid.useBreakpoint();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const isAdmin = role === 'ADMIN';
  // 관리자 메뉴 7개는 lg 폭에 한 줄로 들어가지 않아 관리자는 xl부터 펼치고, 그보다 좁으면 Drawer로 연다.
  const collapsed = isAdmin ? !screens.xl : !screens.lg;
  // 배지는 장바구니 줄 수. 로그인하지 않았으면 조회하지 않는다.
  const { data: cartItems } = useQuery({
    ...cartQueries.items(),
    enabled: status === 'authenticated',
  });
  const cartCount = status === 'authenticated' ? (cartItems?.length ?? 0) : 0;

  const handleOpenDrawer = () => setDrawerOpen(true);
  const handleCloseDrawer = () => setDrawerOpen(false);
  const handleNavigate = (key: (typeof NAV_ITEMS)[number]['key']) => {
    if (key === 'fridge') navigate(ROUTES.fridge);
    if (key === 'recipes') navigate(ROUTES.recipes);
    if (key === 'products') navigate(ROUTES.products);
    if (key === 'cart') navigate(ROUTES.cart);
    if (key === 'mypage') navigate(status === 'authenticated' ? ROUTES.mypage : ROUTES.login);
  };
  // 상단 검색은 레시피·상품 등을 함께 찾는 통합 검색이라 별도 검색 화면으로 보낸다.
  const handleSearch = (value: string) => {
    const keyword = value.trim();
    if (!keyword) return;
    navigate(`${ROUTES.search}?${new URLSearchParams({ keyword })}`);
  };
  const isCurrent = (key: (typeof NAV_ITEMS)[number]['key']) =>
    (key === 'fridge' && pathname === ROUTES.fridge) ||
    (key === 'recipes' && pathname.startsWith(ROUTES.recipes)) ||
    (key === 'products' && pathname.startsWith(ROUTES.products)) ||
    (key === 'cart' && pathname === ROUTES.cart) ||
    (key === 'mypage' && pathname.startsWith(ROUTES.mypage));
  const isAdminCurrent = (item: (typeof ADMIN_NAV_ITEMS)[number]) =>
    item.activePaths.some((path) => pathname.startsWith(path));
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
        <Link to={isAdmin ? ROUTES.adminMembers : ROUTES.home} className={styles.brand}>
          <span className={styles.logoMark} aria-hidden="true">
            <span className={styles.logoDoor} />
          </span>
          <Typography.Text strong className={styles.brandText}>
            냉장고를 부탁해
          </Typography.Text>
        </Link>

        {!isAdmin && (
          <div className={styles.searchWrapper}>
            <Input
              key={currentKeyword}
              defaultValue={currentKeyword}
              prefix={<SearchOutlined />}
              placeholder="재료나 요리를 검색해 보세요"
              aria-label="재료나 요리 검색"
              maxLength={SEARCH_KEYWORD_MAX_LENGTH}
              enterKeyHint="search"
              onPressEnter={(event) => handleSearch(event.currentTarget.value)}
              className={styles.searchInput}
            />
          </div>
        )}

        {isAdmin ? (
          <nav className={styles.adminNav} aria-label="관리자 메뉴">
            {ADMIN_NAV_ITEMS.map((item) => (
              <Button
                key={item.path}
                type="text"
                aria-current={isAdminCurrent(item) ? 'page' : undefined}
                className={isAdminCurrent(item) ? styles.navButtonCurrent : styles.navButton}
                onClick={() => navigate(item.path)}
              >
                {item.label}
              </Button>
            ))}
          </nav>
        ) : (
          <nav className={styles.desktopNav} aria-label="주요 메뉴">
            {NAV_ITEMS.map((item) => (
              <Button
                key={item.key}
                type="text"
                aria-current={isCurrent(item.key) ? 'page' : undefined}
                className={isCurrent(item.key) ? styles.navButtonCurrent : styles.navButton}
                onClick={() => handleNavigate(item.key)}
              >
                <Flex align="center" gap="small">
                  <span>{item.label}</span>
                  {item.key === 'cart' && <Badge count={cartCount} size="small" />}
                </Flex>
              </Button>
            ))}
          </nav>
        )}

        <Flex align="center" gap="small" className={styles.actions}>
          {!collapsed && isAdmin && (
            <Button type="text" onClick={() => navigate(ROUTES.account)}>
              내 정보
            </Button>
          )}
          {!collapsed && status === 'authenticated' && (
            <Button type="text" onClick={handleLogout} loading={logoutMutation.isPending}>
              {isAdmin ? (
                <span className={styles.nickname} title={nickname}>
                  {nickname}
                </span>
              ) : (
                nickname
              )}{' '}
              로그아웃
            </Button>
          )}
          {!collapsed && status === 'anonymous' && (
            <Button type="text" onClick={() => navigate(ROUTES.login)}>
              로그인
            </Button>
          )}
          {!isAdmin && <NotificationBell />}
          {collapsed && (
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
        open={collapsed && drawerOpen}
        onClose={handleCloseDrawer}
      >
        <Flex vertical gap="middle">
          {!isAdmin && (
            <Input
              key={currentKeyword}
              defaultValue={currentKeyword}
              prefix={<SearchOutlined />}
              placeholder="재료나 요리를 검색해 보세요"
              aria-label="모바일 재료나 요리 검색"
              maxLength={SEARCH_KEYWORD_MAX_LENGTH}
              enterKeyHint="search"
              onPressEnter={(event) => {
                if (!event.currentTarget.value.trim()) return;
                handleCloseDrawer();
                handleSearch(event.currentTarget.value);
              }}
            />
          )}
          <Flex vertical gap="xs" role="navigation" aria-label="모바일 메뉴">
            {isAdmin
              ? ADMIN_NAV_ITEMS.map((item) => (
                  <Button
                    key={item.path}
                    type="text"
                    block
                    aria-current={isAdminCurrent(item) ? 'page' : undefined}
                    onClick={() => {
                      handleCloseDrawer();
                      navigate(item.path);
                    }}
                    className={styles.drawerNavButton}
                  >
                    {item.label}
                  </Button>
                ))
              : NAV_ITEMS.map((item) => (
                  <Button
                    key={item.key}
                    type="text"
                    block
                    aria-current={isCurrent(item.key) ? 'page' : undefined}
                    onClick={() => {
                      handleCloseDrawer();
                      handleNavigate(item.key);
                    }}
                    className={styles.drawerNavButton}
                  >
                    <Flex align="center" justify="space-between" className={styles.drawerNavInner}>
                      <span>{item.label}</span>
                      {item.key === 'cart' && <Badge count={cartCount} size="small" />}
                    </Flex>
                  </Button>
                ))}
          </Flex>
          <Divider size="small" />
          {/* 계정 영역: 메뉴와 같은 왼쪽 정렬 */}
          <Flex vertical gap="xs">
            {status === 'authenticated' && (
              <Typography.Text type="secondary" ellipsis className={styles.drawerNickname}>
                {nickname}
              </Typography.Text>
            )}
            {isAdmin && (
              <Button
                type="text"
                block
                onClick={() => {
                  handleCloseDrawer();
                  navigate(ROUTES.account);
                }}
                className={styles.drawerNavButton}
              >
                내 정보
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
                className={styles.drawerNavButton}
              >
                로그아웃
              </Button>
            )}
            {status === 'anonymous' && (
              <Button
                type="text"
                block
                onClick={() => {
                  handleCloseDrawer();
                  navigate(ROUTES.login);
                }}
                className={styles.drawerNavButton}
              >
                로그인
              </Button>
            )}
          </Flex>
        </Flex>
      </Drawer>
    </Layout.Header>
  );
}

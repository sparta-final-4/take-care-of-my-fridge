import {
  CloseOutlined,
  MenuOutlined,
  SearchOutlined,
  ShoppingOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Badge, Button, Divider, Drawer, Dropdown, Flex, Layout, Typography } from 'antd';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { setSession } from '@/common/auth/session';
import { sessionId } from '@/common/auth/sessionId';
import { useMemberEventStream } from '@/common/sse/useMemberEventStream';
import { useScreens } from '@/common/hooks/useScreens';
import { logout } from '@/domains/auth/api';
import { cartQueries } from '@/domains/cart/queries';
import { chatSessionEventListener } from '@/domains/chat/chatSessionEvent';
import { NotificationBell } from '@/domains/notification/components/NotificationBell';
import { notificationEventListener } from '@/domains/notification/notificationEvent';
import { SearchKeywordInput } from '@/domains/search/components/SearchKeywordInput';

import { CartDrawer } from './CartDrawer';
import styles from './Header.module.scss';

// 레퍼런스(row7seeds.com)처럼 핵심 메뉴만 글자로 두고, 장바구니·계정·검색은 오른쪽 아이콘으로 둔다.
const NAV_ITEMS = [
  { key: 'fridge', label: '내 냉장고', path: ROUTES.fridge },
  { key: 'recipes', label: '레시피', path: ROUTES.recipes },
  { key: 'products', label: '재료/밀키트', path: ROUTES.products },
] as const;

// 관리자에게는 이 메뉴만 보인다. 경로가 activePaths 중 하나로 시작하면 현재 위치로 표시한다(하위 화면 포함).
// hidden: 메뉴에서만 숨긴다(화면·주소는 그대로 있어 주소로 들어가면 열린다). 다시 보이려면 hidden을 지운다.
const ADMIN_NAV_ITEMS: ReadonlyArray<{
  label: string;
  path: string;
  activePaths: readonly string[];
  hidden?: boolean;
}> = [
  { label: '회원 관리', path: ROUTES.adminMembers, activePaths: [ROUTES.adminMembers] },
  {
    label: '작업 이력',
    path: ROUTES.adminAudit,
    activePaths: [ROUTES.adminAudit],
    hidden: true,
  },
  {
    label: '판매 예측',
    path: ROUTES.adminForecast,
    activePaths: [ROUTES.adminForecast],
    hidden: true,
  },
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
];
// 메뉴에 그리는 항목(숨김 제외). 숨긴 칸은 아예 그리지 않아 사이 여백도 남지 않는다.
const VISIBLE_ADMIN_NAV_ITEMS = ADMIN_NAV_ITEMS.filter((item) => !item.hidden);

// 메인에서 이만큼(px) 넘게 스크롤하면 투명하던 헤더에 바탕을 채운다.
const SCROLL_SOLID_THRESHOLD = 8;
const subscribeScroll = (onChange: () => void) => {
  window.addEventListener('scroll', onChange, { passive: true });
  return () => window.removeEventListener('scroll', onChange);
};
const isScrolledPastTop = () => window.scrollY > SCROLL_SOLID_THRESHOLD;

// 회원 실시간 신호에 붙일 도메인 처리기. 바뀌면 다시 연결하므로 모듈 상수로 둔다.
const MEMBER_EVENT_LISTENERS = [notificationEventListener, chatSessionEventListener];

export function Header() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  // 검색 화면에서는 현재 검색어를 입력칸에 보여 주고, 다른 화면으로 가면 비운다.
  const isSearchPage = pathname === ROUTES.search;
  const currentKeyword = isSearchPage ? (searchParams.get('keyword') ?? '') : '';
  const status = useAuthStore((state) => state.status);
  const role = useAuthStore((state) => state.member?.role);
  const nickname = useAuthStore((state) => state.member?.nickname);
  const logoutMutation = useMutation({ mutationFn: logout });
  const screens = useScreens();
  const [drawerOpen, setDrawerOpen] = useState(false);
  // 돋보기를 누르면 헤더 아래에 검색 줄이 열린다. 검색 화면에서는 늘 열어 둔다.
  // 장바구니 서랍처럼 연 화면의 경로를 기억해, 다른 화면으로 이동하면 저절로 닫힌다.
  const [searchOpenPath, setSearchOpenPath] = useState<string | null>(null);
  const searchOpen = searchOpenPath === pathname;
  const searchPanelRef = useRef<HTMLDivElement>(null);
  const searchButtonRef = useRef<HTMLButtonElement>(null);
  // 장바구니 서랍을 연 화면의 경로. 다른 화면으로 이동하면(주문 진행 등) 저절로 닫힌다.
  const [cartOpenPath, setCartOpenPath] = useState<string | null>(null);
  const cartOpen = cartOpenPath === pathname;
  const showSearch = searchOpen || isSearchPage;
  const isAdmin = role === 'ADMIN';
  // 메인 첫 화면에서는 상단바 없이 사진 위에 투명하게 겹친다. 마우스를 올리면(CSS) 바탕색이 나타나고,
  // 사진을 지나 스크롤했거나 검색 줄을 열면 계속 바탕을 채운다(밝은 본문 위에서 종이색 글자가 안 보이므로).
  const isHome = pathname === ROUTES.home;
  const scrolled = useSyncExternalStore(subscribeScroll, isScrolledPastTop, () => false);
  const overlay = isHome && !isAdmin && !scrolled && !showSearch;
  // 관리자 메뉴 7개는 lg 폭에 한 줄로 들어가지 않아 관리자는 xl부터 펼치고, 그보다 좁으면 Drawer로 연다.
  const collapsed = isAdmin ? !screens.xl : !screens.lg;
  // 배지는 장바구니 줄 수. 로그인하지 않았으면 조회하지 않는다.
  const { data: cartItems } = useQuery({
    ...cartQueries.items(),
    enabled: status === 'authenticated',
  });
  const cartCount = status === 'authenticated' ? (cartItems?.length ?? 0) : 0;
  // 실시간 신호는 앱 전체에서 여기 하나만 연다. 알림 종을 보여 주는 로그인한 일반 회원만(관리자는 열지 않음).
  useMemberEventStream(status === 'authenticated' && !isAdmin, MEMBER_EVENT_LISTENERS);

  const handleOpenDrawer = () => setDrawerOpen(true);
  const handleCloseDrawer = () => setDrawerOpen(false);
  // 상단 검색은 레시피·상품 등을 함께 찾는 통합 검색이라 별도 검색 화면으로 보낸다.
  const handleSearch = (value: string) => {
    const keyword = value.trim();
    if (!keyword) return;
    setSearchOpenPath(null);
    navigate(`${ROUTES.search}?${new URLSearchParams({ keyword })}`);
  };
  const handleToggleSearch = () => {
    if (showSearch && !isSearchPage) {
      setSearchOpenPath(null);
      return;
    }
    setSearchOpenPath(pathname);
    // 열린 검색 줄의 입력칸으로 바로 포커스를 옮긴다.
    requestAnimationFrame(() => searchPanelRef.current?.querySelector('input')?.focus());
  };
  // 검색 줄 안(닫기 버튼·Esc)이나 어두운 바탕에서 닫을 때는 포커스를 돋보기 버튼으로 먼저 옮긴다.
  // 닫히면 검색 줄이 inert가 되어, 그 안에 포커스가 남으면 문서 맨 앞으로 사라진다.
  const closeSearch = () => {
    searchButtonRef.current?.focus();
    setSearchOpenPath(null);
  };
  // 열린 검색 줄은 Esc로 닫는다(검색 화면의 검색 줄은 늘 열려 있어 제외). 하는 일은 closeSearch와 같다.
  useEffect(() => {
    if (!searchOpen || isSearchPage) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      searchButtonRef.current?.focus();
      setSearchOpenPath(null);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [searchOpen, isSearchPage]);
  const isCurrent = (path: string) => pathname.startsWith(path);
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

  // 계정 아이콘: 로그인 상태면 마이페이지(관리자는 내 정보)·로그아웃 펼침 메뉴, 아니면 로그인 화면으로.
  const accountButton =
    status === 'authenticated' ? (
      <Dropdown
        trigger={['click']}
        placement="bottomRight"
        menu={{
          items: [
            { key: 'nickname', label: nickname, disabled: true },
            { type: 'divider' },
            isAdmin ? { key: 'account', label: '내 정보' } : { key: 'mypage', label: '마이페이지' },
            { key: 'logout', label: '로그아웃' },
          ],
          onClick: ({ key }) => {
            if (key === 'mypage') navigate(ROUTES.mypage);
            if (key === 'account') navigate(ROUTES.account);
            if (key === 'logout') handleLogout();
          },
        }}
      >
        <Button
          type="text"
          icon={<UserOutlined />}
          aria-label="내 계정 메뉴"
          loading={logoutMutation.isPending}
          className={styles.iconButton}
        />
      </Dropdown>
    ) : (
      <Button
        type="text"
        icon={<UserOutlined />}
        aria-label="로그인"
        onClick={() => navigate(ROUTES.login)}
        className={styles.iconButton}
      />
    );

  return (
    <Layout.Header className={overlay ? `${styles.header} ${styles.overlay}` : styles.header}>
      <div className={styles.inner}>
        {/* 좁은 화면: 메뉴 버튼 · 로고 · (오른쪽 끝) 아이콘. 넓은 화면: 로고 · 메뉴 · (오른쪽 끝) 아이콘 */}
        {collapsed && (
          <Button
            type="text"
            icon={<MenuOutlined />}
            aria-label="메뉴 열기"
            onClick={handleOpenDrawer}
            className={styles.iconButton}
          />
        )}

        <Link
          to={isAdmin ? ROUTES.adminMembers : ROUTES.home}
          className={styles.brand}
          aria-label="냉장고를 부탁해 홈"
        >
          <span className={styles.logoMark} aria-hidden="true">
            <span className={styles.logoDoor} />
          </span>
          <span className={styles.brandText}>냉장고를 부탁해</span>
        </Link>

        {isAdmin ? (
          <nav className={styles.adminNav} aria-label="관리자 메뉴">
            {VISIBLE_ADMIN_NAV_ITEMS.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                aria-current={isAdminCurrent(item) ? 'page' : undefined}
                className={styles.navLink}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        ) : (
          <nav className={styles.desktopNav} aria-label="주요 메뉴">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.key}
                to={item.path}
                aria-current={isCurrent(item.path) ? 'page' : undefined}
                className={styles.navLink}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        )}

        <div className={styles.actions}>
          {isAdmin ? (
            // 일반 화면과 같은 사람 아이콘: 누르면 닉네임 · 내 정보 · 로그아웃(좁은 화면은 왼쪽 메뉴에 있다)
            !collapsed && accountButton
          ) : (
            <>
              <Button
                type="text"
                icon={showSearch && !isSearchPage ? <CloseOutlined /> : <SearchOutlined />}
                // 좁은 화면은 공간이 모자라 검색을 왼쪽 메뉴(Drawer)에서 한다.
                aria-label={showSearch && !isSearchPage ? '검색 닫기' : '검색 열기'}
                aria-expanded={showSearch}
                aria-controls="header-search"
                ref={searchButtonRef}
                onClick={handleToggleSearch}
                className={`${styles.iconButton} ${styles.searchButton}`}
              />
              <span className={styles.desktopOnly}>{accountButton}</span>
              <NotificationBell />
              <Badge count={cartCount} size="small" offset={[-6, 6]}>
                <Button
                  type="text"
                  icon={<ShoppingOutlined />}
                  aria-label={cartCount > 0 ? `장바구니, ${cartCount}개` : '장바구니'}
                  aria-haspopup="dialog"
                  onClick={() => setCartOpenPath(pathname)}
                  className={styles.iconButton}
                />
              </Badge>
            </>
          )}
        </div>
      </div>

      {/* 레퍼런스처럼 헤더 바로 아래 화면 폭 전체에 뜨는 검색 줄: [돋보기 입력칸] [닫기].
          검색 화면에서는 늘 열려 있으므로 본문 흐름 안에 두고(뒤 가림·닫기 없음), 그 밖에서는 본문 위에 띄운다. */}
      {/* 닫혀 있어도 그려 두고 열림 상태만 바꿔, 열고 닫을 때 부드럽게 내려오고 사라지게 한다.
          닫혀 있는 동안은 inert로 키보드 이동·화면 읽기에서 뺀다. */}
      {!isAdmin && (
        <>
          {!isSearchPage && (
            <button
              type="button"
              tabIndex={-1}
              aria-label="검색 닫기"
              className={
                showSearch
                  ? `${styles.searchBackdrop} ${styles.searchBackdropOpen}`
                  : styles.searchBackdrop
              }
              onClick={closeSearch}
            />
          )}
          <div
            id="header-search"
            ref={searchPanelRef}
            inert={!showSearch}
            className={[
              styles.searchPanel,
              !isSearchPage && styles.searchFloating,
              showSearch && styles.searchOpen,
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <div className={styles.searchInner}>
              <SearchKeywordInput
                key={currentKeyword}
                initialKeyword={currentKeyword}
                ariaLabel="재료나 요리 검색"
                onSearch={handleSearch}
                className={styles.searchInput}
              />
              {!isSearchPage && (
                <Button
                  type="text"
                  icon={<CloseOutlined />}
                  aria-label="검색 닫기"
                  onClick={closeSearch}
                  className={styles.iconButton}
                />
              )}
            </div>
          </div>
        </>
      )}

      <CartDrawer open={cartOpen} onClose={() => setCartOpenPath(null)} />

      <Drawer
        title="냉장고를 부탁해"
        placement="left"
        open={collapsed && drawerOpen}
        onClose={handleCloseDrawer}
      >
        <Flex vertical gap="middle">
          {!isAdmin && (
            <SearchKeywordInput
              key={currentKeyword}
              initialKeyword={currentKeyword}
              ariaLabel="모바일 재료나 요리 검색"
              onSearch={(keyword) => {
                if (!keyword.trim()) return;
                handleCloseDrawer();
                handleSearch(keyword);
              }}
            />
          )}
          <Flex vertical gap="xs" role="navigation" aria-label="모바일 메뉴">
            {isAdmin
              ? VISIBLE_ADMIN_NAV_ITEMS.map((item) => (
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
                    aria-current={isCurrent(item.path) ? 'page' : undefined}
                    onClick={() => {
                      handleCloseDrawer();
                      navigate(item.path);
                    }}
                    className={styles.drawerNavButton}
                  >
                    {item.label}
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
            {isAdmin ? (
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
            ) : (
              status === 'authenticated' && (
                <Button
                  type="text"
                  block
                  onClick={() => {
                    handleCloseDrawer();
                    navigate(ROUTES.mypage);
                  }}
                  className={styles.drawerNavButton}
                >
                  마이페이지
                </Button>
              )
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

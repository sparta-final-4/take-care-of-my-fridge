import type { ComponentType } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';

import { RouteErrorPage } from '@/common/components/RouteErrorPage';
import { RequireAuth } from '@/common/auth/RequireAuth';
import { MainLayout, MainLayoutFallback } from '@/layout/MainLayout';
import { MyPageLayout } from '@/layout/MyPageLayout';

import { ROUTES } from './paths';

const page = (load: () => Promise<{ default: ComponentType }>) => async () => ({
  Component: (await load()).default,
});

export const router = createBrowserRouter([
  {
    Component: MainLayout,
    HydrateFallback: MainLayoutFallback,
    errorElement: <RouteErrorPage />,
    children: [
      { path: ROUTES.home, lazy: page(() => import('@/pages/MainPage')) },
      { path: ROUTES.login, lazy: page(() => import('@/pages/auth/LoginPage')) },
      { path: ROUTES.signup, lazy: page(() => import('@/pages/auth/SignUpPage')) },
      { path: ROUTES.signupComplete, lazy: page(() => import('@/pages/auth/SignUpCompletePage')) },
      { path: ROUTES.recipes, lazy: page(() => import('@/pages/recipe/RecipesPage')) },
      {
        path: ROUTES.recipeDetail,
        lazy: page(() => import('@/pages/recipe/RecipeDetailPage')),
      },
      {
        // 상품 주소(/products/:productId)를 목록 아래에 둔다. 화면은 ProductsPage가 목록 위 상품 모달로 그리므로
        // 따로 불러올 페이지 코드가 없다(모달을 열 때 목록이 흐려지지 않는다). MainLayout도 이 id를 쓴다.
        id: 'products',
        path: ROUTES.products,
        lazy: page(() => import('@/pages/product/ProductsPage')),
        children: [{ path: ROUTES.productDetail }],
      },
      { path: ROUTES.search, lazy: page(() => import('@/pages/search/SearchPage')) },
      {
        Component: RequireAuth,
        children: [
          { path: ROUTES.fridge, lazy: page(() => import('@/pages/fridge/FridgePage')) },
          { path: ROUTES.account, lazy: page(() => import('@/pages/member/AccountPage')) },
          {
            // MainLayout이 마이페이지 안의 이동을 구분할 때 쓴다.
            id: 'mypage-layout',
            Component: MyPageLayout,
            children: [
              { path: ROUTES.mypage, element: <Navigate to={ROUTES.mypageRecipes} replace /> },
              {
                path: ROUTES.mypageRecipes,
                lazy: page(() => import('@/pages/mypage/MyPageRecipesPage')),
              },
              {
                path: ROUTES.mypageChats,
                lazy: page(() => import('@/pages/mypage/MyPageChatsPage')),
              },
              {
                path: ROUTES.mypageProfile,
                lazy: page(() => import('@/pages/mypage/MyPageProfilePage')),
              },
              {
                path: ROUTES.mypageOrders,
                lazy: page(() => import('@/pages/mypage/MyPageOrdersPage')),
              },
              {
                path: ROUTES.mypageOrderDetail,
                lazy: page(() => import('@/pages/mypage/MyPageOrderDetailPage')),
              },
              {
                path: ROUTES.mypageOrderRefund,
                lazy: page(() => import('@/pages/mypage/MyPageRefundRequestPage')),
              },
              {
                path: ROUTES.mypageOrderRefunds,
                lazy: page(() => import('@/pages/mypage/MyPageRefundDetailPage')),
              },
              {
                path: ROUTES.mypageCards,
                lazy: page(() => import('@/pages/mypage/MyPageCardsPage')),
              },
            ],
          },
          {
            path: ROUTES.orderCheckout,
            lazy: page(() => import('@/pages/order/OrderCheckoutPage')),
          },
        ],
      },
      {
        id: 'admin',
        element: <RequireAuth admin />,
        children: [
          { path: ROUTES.adminMembers, lazy: page(() => import('@/pages/admin/AdminMembersPage')) },
          { path: ROUTES.adminAudit, lazy: page(() => import('@/pages/admin/AdminAuditPage')) },
          {
            path: ROUTES.adminForecast,
            lazy: page(() => import('@/pages/admin/AdminForecastPage')),
          },
          { path: ROUTES.adminDeals, lazy: page(() => import('@/pages/admin/AdminDealPage')) },
          {
            path: ROUTES.adminProducts,
            lazy: page(() => import('@/pages/admin/AdminProductsPage')),
          },
          {
            path: ROUTES.adminProductNew,
            lazy: page(() => import('@/pages/admin/AdminProductCreatePage')),
          },
          {
            path: ROUTES.adminProductDetail,
            lazy: page(() => import('@/pages/admin/AdminProductDetailPage')),
          },
          {
            path: ROUTES.adminSaleEventNew,
            lazy: page(() => import('@/pages/admin/AdminSaleEventCreatePage')),
          },
          {
            path: ROUTES.adminReviewSummary,
            lazy: page(() => import('@/pages/admin/AdminReviewSummaryPage')),
          },
          {
            path: ROUTES.adminSettlements,
            lazy: page(() => import('@/pages/admin/AdminSettlementPage')),
          },
        ],
      },
    ],
  },
  { path: '*', lazy: page(() => import('@/pages/NotFoundPage')) },
]);

// requestIdleCallback이 없는 브라우저(Safari 일부)에서 첫 화면 뒤에 받기 시작하는 시점
const PREFETCH_FALLBACK_DELAY_MS = 1000;

// 첫 화면을 띄운 뒤 쉬는 시간에 사용자 화면 코드를 미리 받아, 메뉴 이동 때 기다리지 않게 한다.
// 관리자 화면은 관리자만 쓰므로 미리 받지 않는다.
function prefetchPages(routes: typeof router.routes) {
  for (const route of routes) {
    if (route.id === 'admin') continue;
    if (typeof route.lazy === 'function') void route.lazy().catch(() => undefined);
    if (route.children) prefetchPages(route.children);
  }
}

function schedulePrefetch() {
  const run = () => prefetchPages(router.routes);
  if ('requestIdleCallback' in window) window.requestIdleCallback(run);
  else setTimeout(run, PREFETCH_FALLBACK_DELAY_MS);
}

if (router.state.initialized) {
  schedulePrefetch();
} else {
  const unsubscribe = router.subscribe((state) => {
    if (!state.initialized) return;
    unsubscribe();
    schedulePrefetch();
  });
}

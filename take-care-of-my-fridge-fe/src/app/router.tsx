import type { ComponentType } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';

import { RouteErrorPage } from '@/common/components/RouteErrorPage';
import { RequireAuth } from '@/common/auth/RequireAuth';
import { MainLayout } from '@/layout/MainLayout';
import { MyPageLayout } from '@/layout/MyPageLayout';

import { ROUTES } from './paths';

const page = (load: () => Promise<{ default: ComponentType }>) => async () => ({
  Component: (await load()).default,
});

export const router = createBrowserRouter([
  {
    Component: MainLayout,
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
      { path: ROUTES.products, lazy: page(() => import('@/pages/product/ProductsPage')) },
      {
        path: ROUTES.productDetail,
        lazy: page(() => import('@/pages/product/ProductDetailPage')),
      },
      {
        Component: RequireAuth,
        children: [
          { path: ROUTES.fridge, lazy: page(() => import('@/pages/fridge/FridgePage')) },
          { path: ROUTES.account, lazy: page(() => import('@/pages/member/AccountPage')) },
          {
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
            ],
          },
          { path: ROUTES.cart, lazy: page(() => import('@/pages/order/CartPage')) },
          {
            path: ROUTES.orderCheckout,
            lazy: page(() => import('@/pages/order/OrderCheckoutPage')),
          },
        ],
      },
      {
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
            path: ROUTES.adminSaleEventNew,
            lazy: page(() => import('@/pages/admin/AdminSaleEventCreatePage')),
          },
        ],
      },
    ],
  },
  { path: '*', lazy: page(() => import('@/pages/NotFoundPage')) },
]);

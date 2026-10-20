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
            ],
          },
        ],
      },
      {
        element: <RequireAuth admin />,
        children: [
          { path: ROUTES.adminMembers, lazy: page(() => import('@/pages/admin/AdminMembersPage')) },
          { path: ROUTES.adminAudit, lazy: page(() => import('@/pages/admin/AdminAuditPage')) },
        ],
      },
    ],
  },
  { path: '*', lazy: page(() => import('@/pages/NotFoundPage')) },
]);

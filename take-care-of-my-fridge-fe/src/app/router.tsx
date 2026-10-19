import type { ComponentType } from 'react';
import { createBrowserRouter } from 'react-router';

import { RouteErrorPage } from '@/common/components/RouteErrorPage';
import { RequireAuth } from '@/common/auth/RequireAuth';
import { MainLayout } from '@/layout/MainLayout';

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
      { path: ROUTES.login, lazy: page(() => import('@/pages/LoginPage')) },
      { path: ROUTES.signup, lazy: page(() => import('@/pages/SignUpPage')) },
      { path: ROUTES.signupComplete, lazy: page(() => import('@/pages/SignUpCompletePage')) },
      {
        Component: RequireAuth,
        children: [
          { path: ROUTES.fridge, lazy: page(() => import('@/pages/FridgePage')) },
          { path: ROUTES.account, lazy: page(() => import('@/pages/AccountPage')) },
        ],
      },
      {
        element: <RequireAuth admin />,
        children: [
          { path: ROUTES.adminMembers, lazy: page(() => import('@/pages/AdminMembersPage')) },
          { path: ROUTES.adminAudit, lazy: page(() => import('@/pages/AdminAuditPage')) },
        ],
      },
    ],
  },
  { path: '*', lazy: page(() => import('@/pages/NotFoundPage')) },
]);

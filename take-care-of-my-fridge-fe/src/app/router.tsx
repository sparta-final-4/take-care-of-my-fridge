import type { ComponentType } from 'react';
import { createBrowserRouter } from 'react-router';

import { RouteErrorPage } from '@/common/components/RouteErrorPage';
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
    ],
  },
  { path: '*', lazy: page(() => import('@/pages/NotFoundPage')) },
]);

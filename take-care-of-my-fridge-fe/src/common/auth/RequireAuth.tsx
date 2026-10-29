import { Button, Result } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router';

import { ROUTES } from '@/app/paths';
import { RouteFallback } from '@/common/components/RouteFallback';

import { useAuthStore } from './authStore';
import { initSession } from './initialize';

export function RequireAuth({ admin = false }: { admin?: boolean }) {
  const status = useAuthStore((state) => state.status);
  const role = useAuthStore((state) => state.member?.role);
  const reason = useAuthStore((state) => state.reason);
  const location = useLocation();

  if (status === 'initializing') return <RouteFallback />;
  if (status === 'error') {
    return (
      <Result
        status="warning"
        title="로그인 상태를 확인하지 못했습니다"
        extra={<Button onClick={() => void initSession()}>다시 시도</Button>}
      />
    );
  }
  if (status === 'anonymous') {
    const redirect = `${location.pathname}${location.search}${location.hash}`;
    const params = new URLSearchParams({ redirect });
    if (reason) params.set('reason', reason);
    return <Navigate to={`${ROUTES.login}?${params}`} replace />;
  }
  if (admin && role !== 'ADMIN') {
    return <Result status="403" title="관리자 권한이 필요합니다" />;
  }
  return <Outlet />;
}

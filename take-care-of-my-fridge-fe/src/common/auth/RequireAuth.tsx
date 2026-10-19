import { Button, Result, Spin } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router';

import { ROUTES } from '@/app/paths';

import { useAuthStore } from './authStore';
import { initSession } from './initialize';

export function RequireAuth({ admin = false }: { admin?: boolean }) {
  const status = useAuthStore((state) => state.status);
  const role = useAuthStore((state) => state.member?.role);
  const reason = useAuthStore((state) => state.reason);
  const location = useLocation();

  if (status === 'initializing') return <Spin fullscreen tip="로그인 상태를 확인하고 있습니다" />;
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

import { BellOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Popover } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';

import { notificationQueries } from '../queries';
import { useNotificationStream } from '../useNotificationStream';
import { NotificationList } from './NotificationList';
import { NotificationTitle } from './NotificationTitle';

export function NotificationBell() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const queryClient = useQueryClient();
  const authenticated = useAuthStore((state) => state.status) === 'authenticated';
  const [open, setOpen] = useState(false);
  // 탭으로 돌아왔을 때도 다시 불러온다. 로그인하지 않았으면 조회하지 않는다.
  const { data } = useQuery({
    ...notificationQueries.unreadCount(),
    enabled: authenticated,
    refetchOnWindowFocus: true,
  });
  useNotificationStream(authenticated);
  const unreadCount = authenticated ? (data?.unreadCount ?? 0) : 0;

  // 화면을 옮길 때마다 안 읽은 수를 다시 불러온다. 처음 그릴 때는 위 조회가 불러온다.
  const lastPathnameRef = useRef(pathname);
  useEffect(() => {
    if (lastPathnameRef.current === pathname) return;
    lastPathnameRef.current = pathname;
    void queryClient.invalidateQueries({ queryKey: notificationQueries.unreadCount().queryKey });
  }, [pathname, queryClient]);

  const bell = (
    <Badge count={unreadCount} size="small">
      <Button
        type="default"
        shape="circle"
        icon={<BellOutlined />}
        aria-label={unreadCount > 0 ? `알림, 안 읽은 알림 ${unreadCount}개` : '알림'}
        onClick={authenticated ? undefined : () => navigate(ROUTES.login)}
      />
    </Badge>
  );

  if (!authenticated) return bell;

  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
      trigger="click"
      placement="bottomRight"
      // 좁은 화면에서는 종 오른쪽의 메뉴 버튼 때문에 목록이 화면 왼쪽으로 넘친다. 화면 안으로 밀어 넣는다.
      align={{ overflow: { adjustY: true, shiftX: true } }}
      title={<NotificationTitle />}
      content={<NotificationList onNavigate={() => setOpen(false)} />}
      destroyOnHidden
    >
      {bell}
    </Popover>
  );
}

import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Badge, Button, Empty, Flex, Skeleton, Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect } from 'react';
import { Link } from 'react-router';

import { getErrorMessage } from '@/common/api/errors';

import { readAllNotifications } from '../api';
import { notificationQueries } from '../queries';
import styles from './NotificationList.module.scss';

interface NotificationListProps {
  onNavigate: () => void;
}

export function NotificationList({ onNavigate }: NotificationListProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { data, isPending, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteQuery(notificationQueries.list());
  // 실패하면 알리지 않는다. 배지가 그대로 남고 다음에 열 때 다시 보낸다.
  const { mutate: readAll } = useMutation({
    mutationFn: readAllNotifications,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: notificationQueries.unreadCount().queryKey }),
    meta: { skipGlobalError: true },
  });

  // 목록을 받으면 그중 가장 최신 알림까지 읽음으로 바꾼다. 목록에는 받은 그대로의 읽음 여부를 보여 준다.
  const latestNotificationId = data?.pages[0]?.notifications?.[0]?.notificationId;
  useEffect(() => {
    if (latestNotificationId === undefined) return;
    readAll(latestNotificationId);
  }, [latestNotificationId, readAll]);

  const handleMore = async () => {
    const result = await fetchNextPage();
    if (result.isError) message.error(getErrorMessage(result.error));
  };

  if (isPending) {
    return (
      <div className={styles.panel}>
        <Skeleton active title={false} paragraph={{ rows: 4 }} />
      </div>
    );
  }

  if (!data) {
    return (
      <Flex vertical align="center" gap="small" className={styles.panel}>
        <Typography.Text type="secondary">알림을 불러오지 못했습니다</Typography.Text>
        <Button size="small" onClick={() => refetch()}>
          다시 시도
        </Button>
      </Flex>
    );
  }

  const notifications = data.pages.flatMap((page) => page.notifications ?? []);

  if (notifications.length === 0) {
    return (
      <div className={styles.panel}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="알림이 없습니다" />
      </div>
    );
  }

  return (
    <div className={styles.panel}>
      <ul className={styles.list}>
        {notifications.map((notification) => (
          <li key={notification.notificationId}>
            <Link to={notification.linkUrl ?? ''} onClick={onNavigate} className={styles.item}>
              <span className={styles.dot}>
                {!notification.read && <Badge status="processing" />}
              </span>
              <span className={styles.body}>
                <Typography.Text strong={!notification.read}>
                  {notification.content}
                </Typography.Text>
                <Typography.Text type="secondary" className={styles.time}>
                  {dayjs(notification.createdAt).format('YYYY-MM-DD HH:mm')}
                </Typography.Text>
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {hasNextPage && (
        <Button type="link" block loading={isFetchingNextPage} onClick={handleMore}>
          더 보기
        </Button>
      )}
    </div>
  );
}

import { CloseOutlined } from '@ant-design/icons';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Badge, Button, Empty, Flex, Skeleton, Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect } from 'react';
import { Link } from 'react-router';

import { getErrorMessage } from '@/common/api/errors';

import { deleteNotification, readAllNotifications } from '../api';
import { notificationQueries } from '../queries';
import styles from './NotificationList.module.scss';

const CONTENT_SEPARATOR = ' · ';

// "참기름 40% 번개 드롭 · 10월 30일 18:30 시작 · 20개 한정"처럼 오는 문구를 첫 구분점에서 나눠, 시각부터 다음 줄에 둔다.
function splitContent(content: string) {
  const index = content.indexOf(CONTENT_SEPARATOR);
  if (index < 0) return { headline: content, detail: undefined };
  return {
    headline: content.slice(0, index),
    detail: content.slice(index + CONTENT_SEPARATOR.length),
  };
}

interface NotificationListProps {
  onNavigate: () => void;
}

export function NotificationList({ onNavigate }: NotificationListProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { data, isPending, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteQuery(notificationQueries.list());
  const notifications = data?.pages.flatMap((page) => page.notifications ?? []) ?? [];
  // 실패하면 알리지 않는다. 배지가 그대로 남고 다음에 열 때 다시 보낸다.
  const { mutate: readAll } = useMutation({
    mutationFn: readAllNotifications,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: notificationQueries.unreadCount().queryKey }),
    meta: { skipGlobalError: true },
  });
  const {
    mutate: deleteOne,
    isPending: isDeleting,
    variables: deletingId,
  } = useMutation({
    mutationFn: deleteNotification,
    onSuccess: (_, notificationId) => {
      void queryClient.invalidateQueries({ queryKey: notificationQueries.unreadCount().queryKey });
      // 마지막으로 보이던 알림을 지웠으면 다음 알림이 있을 수 있어 목록을 다시 받는다.
      if (notifications.length <= 1) {
        void queryClient.invalidateQueries({ queryKey: notificationQueries.list().queryKey });
        return;
      }
      // 펼쳐 둔 목록을 유지하려고 다시 받지 않고 지운 알림만 뺀다.
      queryClient.setQueryData(
        notificationQueries.list().queryKey,
        (current) =>
          current && {
            ...current,
            pages: current.pages.map((page) => ({
              ...page,
              notifications: page.notifications?.filter(
                (notification) => notification.notificationId !== notificationId,
              ),
            })),
          },
      );
    },
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
        {notifications.map((notification) => {
          const { headline, detail } = splitContent(notification.content ?? '');
          return (
            <li key={notification.notificationId} className={styles.row}>
              <Link to={notification.linkUrl ?? ''} onClick={onNavigate} className={styles.item}>
                <span className={styles.dot}>
                  <Badge status={notification.read ? 'default' : 'processing'} />
                </span>
                <span className={styles.body}>
                  <Typography.Text strong={!notification.read}>
                    {headline}
                    {detail && (
                      <>
                        <br />
                        {detail}
                      </>
                    )}
                  </Typography.Text>
                  <Typography.Text type="secondary" className={styles.time}>
                    {dayjs(notification.createdAt).format('YYYY-MM-DD HH:mm')}
                  </Typography.Text>
                </span>
              </Link>
              <Button
                type="text"
                icon={<CloseOutlined />}
                aria-label="알림 지우기"
                className={styles.delete}
                loading={isDeleting && deletingId === notification.notificationId}
                onClick={() =>
                  notification.notificationId && deleteOne(notification.notificationId)
                }
              />
            </li>
          );
        })}
      </ul>
      {hasNextPage && (
        <Button type="link" block loading={isFetchingNextPage} onClick={handleMore}>
          더 보기
        </Button>
      )}
    </div>
  );
}

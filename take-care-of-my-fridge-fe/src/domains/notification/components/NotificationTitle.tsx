import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Flex } from 'antd';

import { deleteAllNotifications } from '../api';
import { notificationQueries } from '../queries';

export function NotificationTitle() {
  const queryClient = useQueryClient();
  // 목록과 같은 쿼리를 함께 본다. 받은 알림이 있을 때만 모두 지우기를 보여 준다.
  const { data } = useInfiniteQuery(notificationQueries.list());
  const hasNotifications = data?.pages.some((page) => (page.notifications?.length ?? 0) > 0);
  const { mutate: deleteAll, isPending } = useMutation({
    mutationFn: deleteAllNotifications,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationQueries.all }),
  });

  return (
    <Flex justify="space-between" align="center">
      알림
      {hasNotifications && (
        <Button type="link" size="small" loading={isPending} onClick={() => deleteAll()}>
          모두 지우기
        </Button>
      )}
    </Flex>
  );
}

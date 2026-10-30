import {
  notificationApi,
  type NotificationListResponse,
  type UnreadNotificationCountResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// beforeId가 없으면 가장 최신부터, 있으면 그보다 오래된 알림 5개를 받는다.
export async function getMyNotifications(
  beforeId: number | undefined,
  signal?: AbortSignal,
): Promise<NotificationListResponse> {
  return unwrap(notificationApi.getMyNotifications({ request: { beforeId } }, { signal }));
}

export async function getUnreadNotificationCount(
  signal?: AbortSignal,
): Promise<UnreadNotificationCountResponse> {
  return unwrap(notificationApi.getUnreadCount({ signal }));
}

// lastNotificationId 이하의 안 읽은 알림만 읽음이 된다. 그 뒤 도착한 알림은 안 읽음으로 남는다.
export async function readAllNotifications(lastNotificationId: number) {
  return unwrap(notificationApi.readAll({ readAllNotificationsRequest: { lastNotificationId } }));
}

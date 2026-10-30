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

// 없거나 이미 지운 알림이어도 성공으로 끝난다.
export async function deleteNotification(notificationId: number) {
  return unwrap(notificationApi.deleteNotification({ notificationId }));
}

// 화면에 보이지 않은, 목록을 받은 뒤 도착한 알림까지 내 알림을 전부 지운다.
export async function deleteAllNotifications() {
  return unwrap(notificationApi.deleteAll());
}

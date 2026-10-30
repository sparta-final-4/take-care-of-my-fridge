package org.example.takecareofmyfridgebe.domain.notification.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationListRequest;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationListResponse;
import org.example.takecareofmyfridgebe.domain.notification.dto.ReadAllNotificationsRequest;
import org.example.takecareofmyfridgebe.domain.notification.dto.UnreadNotificationCountResponse;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;
import org.example.takecareofmyfridgebe.domain.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int PAGE_SIZE = 5;

    private final NotificationRepository notificationRepository;

    // 한 개를 더 읽어 다음 알림이 있는지 판단한다.
    public NotificationListResponse getMyNotifications(Long memberId, NotificationListRequest request) {
        List<Notification> notifications = notificationRepository.findLatestByMemberId(
                memberId, request.beforeId(), PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasNext = notifications.size() > PAGE_SIZE;
        List<Notification> page = hasNext ? notifications.subList(0, PAGE_SIZE) : notifications;
        return NotificationListResponse.of(page, hasNext);
    }

    public UnreadNotificationCountResponse getUnreadCount(Long memberId) {
        return new UnreadNotificationCountResponse(notificationRepository.countByMemberIdAndReadFalse(memberId));
    }

    @Transactional
    public void readAll(Long memberId, ReadAllNotificationsRequest request) {
        notificationRepository.markAllReadUpTo(memberId, request.lastNotificationId());
    }
}

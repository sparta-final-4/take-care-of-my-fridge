package org.example.takecareofmyfridgebe.domain.notification.dto;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;

public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String content,
        String linkUrl,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getContent(),
                notification.getLinkUrl(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}

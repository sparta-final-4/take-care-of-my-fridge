package org.example.takecareofmyfridgebe.domain.notification.dto;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;

public record NotificationListResponse(List<NotificationResponse> notifications, boolean hasNext) {

    public static NotificationListResponse of(List<Notification> notifications, boolean hasNext) {
        return new NotificationListResponse(notifications.stream()
                .map(NotificationResponse::from)
                .toList(), hasNext);
    }
}

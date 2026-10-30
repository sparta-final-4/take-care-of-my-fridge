package org.example.takecareofmyfridgebe.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
import org.example.takecareofmyfridgebe.domain.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationCreateService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void create(NotificationRequestedMessage message) {
        notificationRepository.save(Notification.builder()
                .memberId(message.memberId())
                .notificationType(message.notificationType())
                .content(message.content())
                .linkUrl(message.linkUrl())
                .dedupeKey(message.dedupeKey())
                .build());
    }
}

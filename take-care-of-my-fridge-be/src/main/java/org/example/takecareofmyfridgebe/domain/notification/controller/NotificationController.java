package org.example.takecareofmyfridgebe.domain.notification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationListRequest;
import org.example.takecareofmyfridgebe.domain.notification.dto.NotificationListResponse;
import org.example.takecareofmyfridgebe.domain.notification.dto.ReadAllNotificationsRequest;
import org.example.takecareofmyfridgebe.domain.notification.dto.UnreadNotificationCountResponse;
import org.example.takecareofmyfridgebe.domain.notification.service.NotificationService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getMyNotifications(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @ModelAttribute NotificationListRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(principal.memberId(), request)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadCount(principal.memberId())));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @RequestBody ReadAllNotificationsRequest request) {
        notificationService.readAll(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @PathVariable Long notificationId) {
        notificationService.delete(principal.memberId(), notificationId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        notificationService.deleteAll(principal.memberId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}

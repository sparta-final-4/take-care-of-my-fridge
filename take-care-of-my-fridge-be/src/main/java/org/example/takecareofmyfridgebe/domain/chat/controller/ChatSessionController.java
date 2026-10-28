package org.example.takecareofmyfridgebe.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 회원의 상담 세션을 시작하거나 기존 진행 중(ACTIVE) 세션을 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatSessionResponse>> startSession(
            @AuthenticationPrincipal AuthPrincipal principal) {
        ChatSessionResponse response = chatSessionService.getOrCreateActiveSession(principal.memberId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 회원의 특정 상담 세션을 조회한다.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long sessionId) {
        ChatSessionResponse response = chatSessionService.getSession(principal.memberId(), sessionId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 회원의 특정 상담 세션을 명시적으로 종료(COMPLETED)한다.
     */
    @PatchMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> completeSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long sessionId) {
        ChatSessionResponse response = chatSessionService.completeSession(principal.memberId(), sessionId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

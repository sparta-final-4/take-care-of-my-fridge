package org.example.takecareofmyfridgebe.domain.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatReplyGenerator;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-sessions/{sessionId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatReplyGenerator chatReplyGenerator;

    /**
     * 상담 세션에 사용자 메시지를 전송하고 가짜(Stub) 답변을 생성하여 두 메시지를 함께 저장한 뒤 반환한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageCreateRequest request) {
        // 1. 세션 검증 (소유자 확인 및 ACTIVE 상태 검증)
        chatMessageService.validateSessionForSending(principal.memberId(), sessionId);

        // 2. 답 생성 (3단계 AI 스트리밍/호출 대비 트랜잭션 외부에서 수행)
        String assistantReply = chatReplyGenerator.generate(sessionId, request.content());

        // 3. 사용자 메시지와 답변 메시지 한 트랜잭션으로 저장
        List<ChatMessageResponse> messages = chatMessageService.saveExchange(
                sessionId,
                request.content(),
                assistantReply
        );

        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    /**
     * 상담 세션의 모든 메시지 기록을 sequence 오름차순으로 조회한다. (끝난 세션도 조회 가능)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long sessionId) {
        List<ChatMessageResponse> messages = chatMessageService.getMessages(principal.memberId(), sessionId);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }
}

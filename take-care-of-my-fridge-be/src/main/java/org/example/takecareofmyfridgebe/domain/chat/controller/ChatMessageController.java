package org.example.takecareofmyfridgebe.domain.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatStreamService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-sessions/{sessionId}/messages")
public class ChatMessageController {

    private final ChatStreamService chatStreamService;
    private final ChatMessageService chatMessageService;

    /**
     * 상담 세션에 사용자 메시지를 전송하고 SSE 스트리밍으로 응답을 수신한다.
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageCreateRequest request) {
        Flux<ServerSentEvent<String>> stream = chatStreamService.streamMessage(
                principal.memberId(),
                sessionId,
                request.content()
        );

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .header("X-Accel-Buffering", "no")
                .body(stream);
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

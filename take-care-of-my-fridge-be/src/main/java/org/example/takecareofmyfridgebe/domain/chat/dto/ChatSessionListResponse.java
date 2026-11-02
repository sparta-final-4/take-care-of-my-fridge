package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.global.response.PageResponse;

// 상담 내역 목록. counts는 상태 필터와 상관없는 탭별 건수
public record ChatSessionListResponse(
        ChatSessionStatusCounts counts,
        PageResponse<ChatSessionSummaryResponse> sessions
) {
}

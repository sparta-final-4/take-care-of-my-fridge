package org.example.takecareofmyfridgebe.domain.chat.dto;

// 상담 내역 탭 건수(메시지가 있는 상담만). ended = 종료 + 만료
public record ChatSessionStatusCounts(
        long total,
        long active,
        long ended
) {
}

package org.example.takecareofmyfridgebe.domain.admin.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.time.LocalDateTime;

public record MemberSummaryResponse(
        Long memberId,
        String email,
        String nickname,
        Member.Role role,
        Member.Status status,
        LocalDateTime createdAt,
        LocalDateTime promotedAt,
        String promotedBy
) {
    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(member.getId(), member.getEmail(), member.getNickname(),
                member.getRole(), member.getStatus(), member.getCreatedAt(), null, null);
    }
}

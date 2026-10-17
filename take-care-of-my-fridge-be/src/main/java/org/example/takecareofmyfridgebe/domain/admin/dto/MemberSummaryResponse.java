package org.example.takecareofmyfridgebe.domain.admin.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;

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
    public static MemberSummaryResponse from(Member member, AdminAuditLog promotion) {
        return new MemberSummaryResponse(member.getId(), member.getEmail(), member.getNickname(),
                member.getRole(), member.getStatus(), member.getCreatedAt(),
                promotion == null ? null : promotion.getCreatedAt(),
                promotion == null ? null : promotion.getMember().getEmail());
    }
}

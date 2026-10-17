package org.example.takecareofmyfridgebe.domain.member.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.entity.MemberTaste;

import java.time.LocalDateTime;
import java.util.List;

public record MyInfoResponse(
        Long memberId,
        String email,
        String nickname,
        Member.Role role,
        Member.Status status,
        List<Member.Taste> tastes,
        LocalDateTime createdAt
) {

    public static MyInfoResponse from(Member member) {
        return new MyInfoResponse(member.getId(), member.getEmail(), member.getNickname(),
                member.getRole(), member.getStatus(),
                member.getTastes().stream().map(MemberTaste::getTaste).toList(),
                member.getCreatedAt());
    }
}
